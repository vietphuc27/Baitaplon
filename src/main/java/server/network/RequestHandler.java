package server.network;

import common.exceptions.AuthenticationException;
import common.models.auction.Auction;
import common.models.auction.AuctionStatus;
import common.models.auction.AutoBidAgent;
import common.models.auction.BidTransaction;
import common.models.item.Art;
import common.models.item.Electronics;
import common.models.item.Item;
import common.models.item.Vehicle;
import common.models.user.Bidder;
import common.models.user.Seller;
import common.models.user.User;
import common.utils.JsonUtils;
import server.manager.AutoBidManager;
import server.repository.AuctionDAO;
import server.repository.UserDAO;
import server.service.AuctionService;
import server.service.AuthService;
import server.service.BidService;
import server.service.ItemService;
import server.service.UserService;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class RequestHandler {
    private final AuthService authService;
    private final UserService userService;
    private final ItemService itemService;
    private final AuctionService auctionService;
    private final BidService bidService;

    public RequestHandler() {
        this.itemService = new ItemService();
        this.authService = new AuthService();
        this.userService = new UserService();
        this.auctionService = new AuctionService(itemService);
        this.bidService = new BidService();
    }

    public String handle(String rawRequest, ClientHandler clientHandler) {
        try {
            Map<String, Object> request = JsonUtils.fromJson(rawRequest, Map.class);
            String action = getRequiredText(request, "action");

            return switch (action) {
                case "login" -> handleLogin(request, clientHandler);
                case "register" -> handleRegister(request);
                case "logout" -> handleLogout(clientHandler);
                case "create_auction" -> handleCreateAuction(request);
                case "end_auction" -> handleEndAuction(request);
                case "get_seller_auctions" -> handleGetSellerAuctions(request);
                case "get_all_users" -> handleGetAllUsers(request);
                case "ban_user" -> handleBanUser(request);
                case "unban_user" -> handleUnbanUser(request);
                case "cancel_auction" -> handleCancelAuction(request);
                case "switch_role" -> handleSwitchRole(request);
                case "place_bid" -> handlePlaceBid(request);
                case "refresh_auctions_status" -> handleRefreshAuctionsStatus();
                case "get_all_auctions" -> handleGetAllAuctions();
                case "get_auction_by_id" -> handleGetAuctionById(request);
                case "get_bidder_bid_history" -> handleGetBidderBidHistory(request);
                case "get_auction_bid_history" -> handleGetAuctionBidHistory(request);
                case "get_user_by_id" -> handleGetUserById(request);
                case "update_wallet_deposit" -> handleUpdateWalletDeposit(request);
                case "register_auto_bid" -> handleRegisterAutoBid(request);
                case "cancel_auto_bid" -> handleCancelAutoBid(request);
                case "get_auto_bid_status" -> handleGetAutoBidStatus(request);
                case "ping" -> JsonUtils.toJson(Map.of("status", "success", "message", "pong"));
                default -> buildError("Action khong duoc ho tro: " + action);
            };
        } catch (AuthenticationException | IllegalArgumentException e) {
            return buildError(e.getMessage());
        } catch (Exception e) {
            return buildError("Loi he thong: " + e.getMessage());
        }
    }

    private String handleLogin(Map<String, Object> request, ClientHandler clientHandler) {
        String username = getRequiredText(request, "username");
        String password = getRequiredText(request, "password");
        String token = authService.login(username, password);
        User user = authService.getCurrentUser(token);
        clientHandler.markAuthenticated(user.getId(), token);
        return buildUserResponse(user, token);
    }

    private String handleRegister(Map<String, Object> request) {
        String username = getRequiredText(request, "username");
        String email = getRequiredText(request, "email");
        String password = getRequiredText(request, "password");
        String role = getRequiredText(request, "role");
        User user = userService.register(username, email, password, role);
        return buildUserResponse(user, null);
    }

    private String handleLogout(ClientHandler clientHandler) {
        if (clientHandler != null) {
            clientHandler.clearAuthentication();
        }
        userService.logout();
        return JsonUtils.toJson(Map.of("status", "success"));
    }

    private String handleCreateAuction(Map<String, Object> request) {
        String sellerId = getRequiredText(request, "sellerId");
        String itemName = getRequiredText(request, "itemName");
        String itemType = getRequiredText(request, "itemType");
        double startPrice = getRequiredDouble(request, "startPrice");
        String description = getRequiredText(request, "description");
        LocalDateTime endTime = LocalDateTime.parse(getRequiredText(request, "endTime"));
        LocalDateTime startTime = LocalDateTime.now();

        // Tao item truoc
        int itemId = generateItemId();
        Item item = buildItem(itemId, sellerId, itemName, description, startPrice, itemType, request);
        itemService.createItem(item);

        // Tao auction
        Auction auction = auctionService.createAuction(sellerId, itemId, startTime, endTime);
        return JsonUtils.toJson(Map.of("status", "success", "auctionId", auction.getAuctionId()));
    }

    private int generateItemId() {
        int id;
        do {
            id = ThreadLocalRandom.current().nextInt(100000, 999999);
        } while (itemService.findById(id).isPresent());
        return id;
    }

    private Item buildItem(int itemId, String sellerId, String itemName, String description, double startPrice, String itemType, Map<String, Object> request) {
        String type = normalizeItemType(itemType);
        return switch (type) {
            case "art" -> {
                String artist = getOptionalText(request, "artist");
                if (artist == null) throw new IllegalArgumentException("Thieu truong artist");
                yield new Art(itemId, itemName, description, startPrice, sellerId, artist);
            }
            case "electronics" ->
                new Electronics(itemId, itemName, description, startPrice, sellerId, 0);
            case "vehicle" -> {
                String mileageStr = getOptionalText(request, "mileage");
                int mileage = mileageStr == null ? 0 : Integer.parseInt(mileageStr);
                yield new Vehicle(itemId, itemName, description, startPrice, sellerId, mileage);
            }
            default -> throw new IllegalArgumentException("Loai san pham khong duoc ho tro: " + itemType);
        };
    }

    private String normalizeItemType(String rawType) {
        String type = rawType.toLowerCase();
        if (type.contains("ngh") || type.contains("art")) return "art";
        if (type.contains("dien") || type.contains("điện") || type.contains("electronic")) return "electronics";
        if (type.contains("phuong") || type.contains("phương") || type.contains("vehicle")) return "vehicle";
        return type;
    }

    private String getOptionalText(Map<String, Object> request, String key) {
        Object value = request.get(key);
        if (value == null) return null;
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private String handleEndAuction(Map<String, Object> request) {
        String sellerId = getRequiredText(request, "sellerId");
        int auctionId = getRequiredInt(request, "auctionId");
        Auction auction = auctionService.endAuctionBySeller(sellerId, auctionId);
        return JsonUtils.toJson(Map.of("status", "success", "auctionId", auction.getAuctionId(), "auctionStatus", String.valueOf(auction.getStatus())));
    }

    private String handleGetSellerAuctions(Map<String, Object> request) {
        String sellerId = getRequiredText(request, "sellerId");
        auctionService.refreshAuctionsStatus();
        AuctionDAO dao = new AuctionDAO();
        List<Auction> auctions = dao.findBySellerId(sellerId);
        List<Map<String, Object>> list = new ArrayList<>();
        for (Auction a : auctions) {
            list.add(toAuctionMap(a));
        }
        return JsonUtils.toJson(Map.of("status", "success", "auctions", list));
    }

    private String handleGetAllUsers(Map<String, Object> request) {
        List<User> users = userService.getAllUsers();
        List<Map<String, Object>> list = new ArrayList<>();
        for (User u : users) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("userId", u.getId());
            m.put("username", u.getUsername());
            m.put("email", u.getEmail());
            m.put("role", u.getRole());
            m.put("userStatus", u.getStatus() == null ? null : u.getStatus().name());
            list.add(m);
        }
        return JsonUtils.toJson(Map.of("status", "success", "users", list));
    }

    private String handleBanUser(Map<String, Object> request) {
        int userId = getRequiredInt(request, "userId");
        userService.banUser(userId);
        return JsonUtils.toJson(Map.of("status", "success"));
    }

    private String handleUnbanUser(Map<String, Object> request) {
        int userId = getRequiredInt(request, "userId");
        userService.unbanUser(userId);
        return JsonUtils.toJson(Map.of("status", "success"));
    }

    private String handleCancelAuction(Map<String, Object> request) {
        int auctionId = getRequiredInt(request, "auctionId");
        AuctionDAO dao = new AuctionDAO();
        Auction auction = dao.findById(auctionId)
                .orElseThrow(() -> new IllegalArgumentException("Khong tim thay phien dau gia: " + auctionId));
        if (auction.getStatus() == AuctionStatus.FINISHED || auction.getStatus() == AuctionStatus.PAID) {
            throw new IllegalArgumentException("Khong the huy phien da ket thuc hoac da thanh toan");
        }
        if (auction.getStatus() == AuctionStatus.CANCELED) {
            throw new IllegalArgumentException("Phien nay da bi huy truoc do");
        }
        auction.setStatus(AuctionStatus.CANCELED);
        dao.update(auction);
        return JsonUtils.toJson(Map.of("status", "success"));
    }

    private String handleSwitchRole(Map<String, Object> request) {
        int userId = getRequiredInt(request, "userId");
        String targetRole = getRequiredText(request, "targetRole");
        User user = userService.findById(userId).orElseThrow(() -> new IllegalArgumentException("Khong tim thay user"));
        User switchedUser = userService.switchRole(user, targetRole);
        return buildUserResponse(switchedUser, null);
    }

    private String handlePlaceBid(Map<String, Object> request) {
        String auctionId = getRequiredText(request, "auctionId");
        int bidderId = getRequiredInt(request, "bidderId");
        double amount = getRequiredDouble(request, "amount");
        User user = userService.findById(bidderId).orElseThrow(() -> new IllegalArgumentException("Khong tim thay bidder"));
        if (!(user instanceof Bidder bidder)) throw new IllegalArgumentException("User khong phai bidder");
        BidTransaction bid = bidService.placeBid(auctionId, bidder, amount);
        return JsonUtils.toJson(Map.of("status", "success", "bidId", bid.getId()));
    }

    private String handleRefreshAuctionsStatus() {
        auctionService.refreshAuctionsStatus();
        return JsonUtils.toJson(Map.of("status", "success"));
    }

    private String handleGetAllAuctions() {
        auctionService.refreshAuctionsStatus();
        AuctionDAO dao = new AuctionDAO();
        List<Auction> auctions = dao.findAll();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Auction a : auctions) {
            list.add(toAuctionMap(a));
        }
        return JsonUtils.toJson(Map.of("status", "success", "auctions", list));
    }

    private String handleGetAuctionById(Map<String, Object> request) {
        int auctionId = getRequiredInt(request, "auctionId");
        AuctionDAO dao = new AuctionDAO();
        Auction a = dao.findById(auctionId).orElse(null);
        if (a == null) return buildError("Khong tim thay phien dau gia: " + auctionId);
        Map<String, Object> m = new LinkedHashMap<>(toAuctionMap(a));
        m.put("status", "success");
        return JsonUtils.toJson(m);
    }

    private String handleGetBidderBidHistory(Map<String, Object> request) {
        String bidderId = getRequiredText(request, "bidderId");
        List<BidTransaction> bids = bidService.getBidderBidHistory(bidderId);
        List<Map<String, Object>> list = new ArrayList<>();
        for (BidTransaction b : bids) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("bidId", b.getId());
            m.put("auctionId", b.getAuctionId());
            m.put("bidderId", b.getBidderId());
            m.put("bidAmount", b.getBidAmount());
            m.put("bidTime", b.getBidTime() != null ? b.getBidTime().toString() : "");
            list.add(m);
        }
        return JsonUtils.toJson(Map.of("status", "success", "bids", list));
    }

    private String handleGetAuctionBidHistory(Map<String, Object> request) {
        String auctionId = getRequiredText(request, "auctionId");
        List<BidTransaction> bids = bidService.getAuctionBidHistory(auctionId);
        List<Map<String, Object>> list = new ArrayList<>();
        for (BidTransaction b : bids) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("bidId", b.getId());
            m.put("auctionId", b.getAuctionId());
            m.put("bidderId", b.getBidderId());
            m.put("bidAmount", b.getBidAmount());
            m.put("bidTime", b.getBidTime() != null ? b.getBidTime().toString() : "");
            list.add(m);
        }
        return JsonUtils.toJson(Map.of("status", "success", "bids", list));
    }

    private String handleGetUserById(Map<String, Object> request) {
        int userId = getRequiredInt(request, "userId");
        Optional<User> opt = userService.findById(userId);
        if (opt.isEmpty()) return buildError("Khong tim thay user: " + userId);
        User u = opt.get();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", "success");
        m.put("userId", u.getId());
        m.put("username", u.getUsername());
        m.put("email", u.getEmail());
        m.put("role", u.getRole());
        m.put("userStatus", u.getStatus() == null ? null : u.getStatus().name());
        if (u instanceof Bidder b && b.getWallet() != null) {
            m.put("walletBalance", b.getWallet().getBalance());
        } else if (u instanceof Seller s && s.getWallet() != null) {
            m.put("walletBalance", s.getWallet().getBalance());
        }
        return JsonUtils.toJson(m);
    }

    private String handleUpdateWalletDeposit(Map<String, Object> request) {
        int userId = getRequiredInt(request, "userId");
        double amount = getRequiredDouble(request, "amount");
        Optional<User> opt = userService.findById(userId);
        if (opt.isEmpty()) return buildError("Khong tim thay user");
        User u = opt.get();
        boolean success = false;
        UserDAO dao = new UserDAO();
        if (u instanceof Bidder b) {
            success = b.getWallet().deposit(amount);
            if (success) dao.update(b);
        } else if (u instanceof Seller s) {
            success = s.getWallet().deposit(amount);
            if (success) dao.update(s);
        } else {
            return buildError("Khong the nap tien cho user nay");
        }
        if (!success) return buildError("Nap tien khong thanh cong");
        return JsonUtils.toJson(Map.of("status", "success"));
    }

    private String handleRegisterAutoBid(Map<String, Object> request) {
        int bidderId = getRequiredInt(request, "bidderId");
        int auctionId = getRequiredInt(request, "auctionId");
        double maxBid = getRequiredDouble(request, "maxBid");
        double increment = getRequiredDouble(request, "increment");
        int agentId = bidService.registerAutoBid(bidderId, auctionId, maxBid, increment);
        return JsonUtils.toJson(Map.of("status", "success", "agentId", agentId));
    }

    private String handleCancelAutoBid(Map<String, Object> request) {
        int agentId = getRequiredInt(request, "agentId");
        AutoBidManager manager = AutoBidManager.getInstance();
        boolean cancelled = manager.cancelAgent(agentId);
        if (!cancelled) return buildError("Khong tim thay agent auto-bid");
        return JsonUtils.toJson(Map.of("status", "success", "message", "Da huy auto-bid"));
    }

    private String handleGetAutoBidStatus(Map<String, Object> request) {
        int bidderId = getRequiredInt(request, "bidderId");
        int auctionId = getRequiredInt(request, "auctionId");
        AutoBidManager manager = AutoBidManager.getInstance();
        boolean hasActive = manager.hasActiveAgent(bidderId, auctionId);
        AutoBidAgent agent = manager.getAgent(bidderId, auctionId);
        if (!hasActive || agent == null) return JsonUtils.toJson(Map.of("status", "success", "active", false));
        return JsonUtils.toJson(Map.of("status", "success", "active", true, "agentId", agent.getAgentId(), "maxBid", agent.getMaxBid(), "increment", agent.getIncrement()));
    }

    private String buildUserResponse(User user, String token) {
        LinkedHashMap<String, Object> response = new LinkedHashMap<>();
        response.put("status", "success");
        if (token != null && !token.isBlank()) response.put("token", token);
        response.put("userId", user.getId());
        response.put("username", user.getUsername());
        response.put("email", user.getEmail());
        response.put("password", user.getPassword());
        response.put("role", user.getRole());
        response.put("userStatus", user.getStatus() == null ? null : user.getStatus().name());
        if (user instanceof Bidder b && b.getWallet() != null) {
            response.put("walletBalance", b.getWallet().getBalance());
        } else if (user instanceof Seller s && s.getWallet() != null) {
            response.put("walletBalance", s.getWallet().getBalance());
        }
        return JsonUtils.toJson(response);
    }

    private String getRequiredText(Map<String, Object> request, String key) {
        Object value = request.get(key);
        if (value == null) throw new IllegalArgumentException(key + " khong duoc de trong");
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) throw new IllegalArgumentException(key + " khong duoc de trong");
        return text;
    }

    private int getRequiredInt(Map<String, Object> request, String key) {
        Object value = request.get(key);
        if (value == null) throw new IllegalArgumentException(key + " khong duoc de trong");
        if (value instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(value).trim()); }
        catch (NumberFormatException e) { throw new IllegalArgumentException(key + " phai la so nguyen"); }
    }

    private double getRequiredDouble(Map<String, Object> request, String key) {
        Object value = request.get(key);
        if (value == null) throw new IllegalArgumentException(key + " khong duoc de trong");
        if (value instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(String.valueOf(value).trim()); }
        catch (NumberFormatException e) { throw new IllegalArgumentException(key + " phai la so"); }
    }

    private String buildError(String message) {
        return JsonUtils.toJson(Map.of("status", "error", "message", message));
    }

    private Map<String, Object> toAuctionMap(Auction auction) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("auctionId", String.valueOf(auction.getAuctionId()));
        map.put("itemName", auction.getItem() != null ? auction.getItem().getName() : "-");
        map.put("itemType", auction.getItem() != null ? auction.getItem().getClass().getSimpleName() : "-");
        map.put("currentPrice", auction.getCurrentHighestBid());
        map.put("sellerId", auction.getSellerId() != null ? auction.getSellerId() : "");
        map.put("auctionStatus", auction.getStatus() != null ? auction.getStatus().name() : "-");
        map.put("startTime", auction.getStartTime() != null ? auction.getStartTime().toString() : "");
        map.put("endTime", auction.getEndTime() != null ? auction.getEndTime().toString() : "");
        return map;
    }
}
