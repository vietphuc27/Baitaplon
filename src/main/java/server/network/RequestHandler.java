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
import server.manager.ConnectionManager;
import server.repository.AuctionDAO;
import server.repository.UserDAO;
import server.service.AuctionService;
import server.service.AuthService;
import server.service.BidService;
import server.service.ItemService;
import server.service.UserService;

import server.service.CloudinaryService;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class RequestHandler {
    // Router trung tam cho toan bo request JSON tu client.
    // Moi action se duoc map vao 1 nhom chuc nang: auth, auction, bid, admin,
    // wallet, auto-bid.
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

    // ===== GROUP 1: Request routing & error boundary =====
    // handle() chi lam 3 viec:
    // 1) parse JSON request
    // 2) route theo "action"
    // 3) chuan hoa error response
    public String handle(String rawRequest, ClientHandler clientHandler) {
        try {
            Map<String, Object> request = JsonUtils.fromJson(rawRequest, Map.class);
            String action = getRequiredText(request, "action");

            return switch (action) {
                case "login" -> handleLogin(request, clientHandler);
                case "register" -> handleRegister(request);
                case "logout" -> handleLogout(clientHandler);
                case "refresh_token" -> handleRefreshToken(request);
                case "create_auction" -> handleCreateAuction(request);
                case "update_open_auction" -> handleUpdateOpenAuction(request);
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

    // ===== GROUP 2: Authentication flows =====
    // login/register/logout/refresh token
    private String handleLogin(Map<String, Object> request, ClientHandler clientHandler) {
        String username = getRequiredText(request, "username");
        String password = getRequiredText(request, "password");
        Map<String, Object> loginResult = authService.login(username, password);
        String accessToken = (String) loginResult.get("accessToken");
        String refreshToken = (String) loginResult.get("refreshToken");
        User user = (User) loginResult.get("user");
        clientHandler.markAuthenticated(user.getId(), accessToken);
        // Trả về accessToken + refreshToken
        LinkedHashMap<String, Object> response = new LinkedHashMap<>();
        response.put("status", "success");
        response.put("accessToken", accessToken);
        response.put("refreshToken", refreshToken);
        response.put("userId", user.getId());
        response.put("username", user.getUsername());
        response.put("email", user.getEmail());
        response.put("role", user.getRole());
        response.put("userStatus", user.getStatus() == null ? null : user.getStatus().name());
        if (user instanceof Bidder b && b.getWallet() != null) {
            response.put("walletBalance", b.getWallet().getBalance());
        } else if (user instanceof Seller s && s.getWallet() != null) {
            response.put("walletBalance", s.getWallet().getBalance());
        }
        return JsonUtils.toJson(response);
    }

    /**
     * Xử lý refresh token — trả về access token mới.
     * Request: { action: "refresh_token", refreshToken: "..." }
     * Response: { status: "success", accessToken: "..." }
     */
    private String handleRefreshToken(Map<String, Object> request) {
        String refreshToken = getRequiredText(request, "refreshToken");
        Map<String, Object> result = authService.refreshAccessToken(refreshToken);
        String newAccessToken = (String) result.get("accessToken");
        return JsonUtils.toJson(Map.of("status", "success", "accessToken", newAccessToken));
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
        String token = clientHandler == null ? null : clientHandler.getAuthToken();
        Integer userId = clientHandler == null ? null : clientHandler.getUserId();
        if (clientHandler != null) {
            clientHandler.clearAuthentication();
        }
        if (token != null && !token.isBlank()) {
            authService.logout(token);
        }
        if (userId != null) {
            AutoBidManager.getInstance().cancelAgentsForBidder(userId);
        }
        return JsonUtils.toJson(Map.of("status", "success"));
    }

    // ===== GROUP 3: Auction lifecycle (seller/admin side) =====
    // create/end/cancel/get seller auctions + helper tao item
    private final CloudinaryService cloudinaryService = new CloudinaryService();

    private String handleCreateAuction(Map<String, Object> request) {
        String sellerId = getRequiredText(request, "sellerId");
        String itemName = getRequiredText(request, "itemName");
        String itemType = getRequiredText(request, "itemType");
        double startPrice = getRequiredDouble(request, "startPrice");
        String description = getRequiredText(request, "description");
        LocalDateTime startTime = LocalDateTime.parse(getRequiredText(request, "startTime"));
        LocalDateTime endTime = LocalDateTime.parse(getRequiredText(request, "endTime"));

        // Tao item truoc
        int itemId = generateItemId();
        Item item = buildItem(itemId, sellerId, itemName, description, startPrice, itemType, request);

        String uploadedImageUrl = null;
        boolean itemCreated = false;
        try {
            String imageBase64 = getOptionalText(request, "imageBase64");
            if (imageBase64 != null && !imageBase64.isEmpty()) {
                uploadedImageUrl = cloudinaryService.uploadImage(imageBase64, "item_" + itemId);
                item.setImageUrl(uploadedImageUrl);
            }

            itemService.createItem(item);
            itemCreated = true;

            Auction auction = auctionService.createAuction(sellerId, itemId, startTime, endTime);
            String response = JsonUtils.toJson(Map.of("status", "success", "auctionId", auction.getAuctionId()));
            broadcastPush("AUCTION_CREATED", auction);
            return response;
        } catch (RuntimeException e) {
            if (itemCreated) {
                cleanupItemAfterFailedAuction(itemId, sellerId);
            }
            if (uploadedImageUrl != null) {
                cleanupUploadedImage(uploadedImageUrl);
            }
            if (e instanceof IllegalArgumentException illegalArgumentException) {
                throw illegalArgumentException;
            }
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    private void cleanupItemAfterFailedAuction(int itemId, String sellerId) {
        try {
            itemService.deleteItem(itemId, sellerId);
        } catch (RuntimeException cleanupError) {
            System.err.println("Khong the xoa item sau khi tao auction that bai: " + cleanupError.getMessage());
        }
    }

    private void cleanupUploadedImage(String imageUrl) {
        try {
            if (!cloudinaryService.deleteImage(imageUrl)) {
                System.err.println("Khong the xoa anh Cloudinary: " + imageUrl);
            }
        } catch (RuntimeException cleanupError) {
            System.err.println("Khong the xoa anh Cloudinary: " + cleanupError.getMessage());
        }
    }

    private String handleUpdateOpenAuction(Map<String, Object> request) {
        int auctionId = getRequiredInt(request, "auctionId");
        String sellerId = getRequiredText(request, "sellerId");
        String itemName = getRequiredText(request, "itemName");
        String itemType = getRequiredText(request, "itemType");
        double startPrice = getRequiredDouble(request, "startPrice");
        String description = getRequiredText(request, "description");
        LocalDateTime startTime = LocalDateTime.parse(getRequiredText(request, "startTime"));
        LocalDateTime endTime = LocalDateTime.parse(getRequiredText(request, "endTime"));

        Auction existing = new AuctionDAO().findById(auctionId)
                .orElseThrow(() -> new IllegalArgumentException("Khong tim thay phien dau gia: " + auctionId));
        if (existing.getItem() == null) {
            throw new IllegalArgumentException("Phien dau gia khong co san pham hop le");
        }

        String previousImageUrl = existing.getItem().getImageUrl();
        Item item = buildItem(existing.getItem().getId(), sellerId, itemName, description, startPrice, itemType,
                request);

        String uploadedImageUrl = null;
        try {
            String imageBase64 = getOptionalText(request, "imageBase64");
            boolean removeImage = getOptionalBoolean(request, "removeImage");
            if (imageBase64 != null) {
                uploadedImageUrl = cloudinaryService.uploadImage(imageBase64, "item_" + existing.getItem().getId());
                item.setImageUrl(uploadedImageUrl);
            } else if (!removeImage && previousImageUrl != null && !previousImageUrl.isBlank()) {
                item.setImageUrl(previousImageUrl);
            }

            Auction auction = auctionService.updateOpenAuctionBySeller(sellerId, auctionId, item, startTime, endTime);
            if (removeImage
                    && previousImageUrl != null
                    && !previousImageUrl.isBlank()) {
                cleanupUploadedImage(previousImageUrl);
            }
            String response = JsonUtils.toJson(Map.of(
                    "status", "success",
                    "auctionId", auction.getAuctionId(),
                    "auctionStatus", String.valueOf(auction.getStatus())));
            broadcastPush("AUCTION_UPDATED", auction);
            return response;
        } catch (RuntimeException e) {
            if (uploadedImageUrl != null) {
                cleanupUploadedImage(uploadedImageUrl);
            }
            if (e instanceof IllegalArgumentException illegalArgumentException) {
                throw illegalArgumentException;
            }
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    private int generateItemId() {
        int id;
        do {
            id = ThreadLocalRandom.current().nextInt(100000, 999999);
        } while (itemService.findById(id).isPresent());
        return id;
    }

    private Item buildItem(int itemId, String sellerId, String itemName, String description, double startPrice,
            String itemType, Map<String, Object> request) {
        String type = normalizeItemType(itemType);
        String mergedDescription = mergeTypeSpecificDescription(type, description, request);
        return switch (type) {
            case "art" -> {
                String artist = getOptionalText(request, "artist");
                if (artist == null)
                    throw new IllegalArgumentException("Thieu truong artist");
                yield new Art(itemId, itemName, mergedDescription, startPrice, sellerId, artist);
            }
            case "electronics" ->
                new Electronics(itemId, itemName, mergedDescription, startPrice, sellerId, 0);
            case "vehicle" -> {
                String mileageStr = getOptionalText(request, "mileage");
                int mileage = mileageStr == null ? 0 : Integer.parseInt(mileageStr);
                yield new Vehicle(itemId, itemName, mergedDescription, startPrice, sellerId, mileage);
            }
            default -> throw new IllegalArgumentException("Loai san pham khong duoc ho tro: " + itemType);
        };
    }

    private String mergeTypeSpecificDescription(String normalizedType, String baseDescription,
            Map<String, Object> request) {
        StringBuilder sb = new StringBuilder(baseDescription == null ? "" : baseDescription.trim());
        appendLine(sb, "Hãng xe", getOptionalText(request, "vehicleBrand"), normalizedType.equals("vehicle"));
        appendLine(sb, "Năm sản xuất", getOptionalText(request, "vehicleYear"), normalizedType.equals("vehicle"));
        appendLine(sb, "Tình trạng", getOptionalText(request, "condition"),
                normalizedType.equals("vehicle") || normalizedType.equals("electronics"));
        appendLine(sb, "Thương hiệu", getOptionalText(request, "brand"), normalizedType.equals("electronics"));
        appendLine(sb, "Model", getOptionalText(request, "model"), normalizedType.equals("electronics"));
        appendLine(sb, "Năm sáng tác", getOptionalText(request, "artYear"), normalizedType.equals("art"));
        appendLine(sb, "Chất liệu", getOptionalText(request, "material"), normalizedType.equals("art"));
        return sb.toString().trim();
    }

    private void appendLine(StringBuilder sb, String key, String value, boolean enabled) {
        if (!enabled || value == null || value.isBlank()) {
            return;
        }
        if (sb.length() > 0) {
            sb.append('\n');
        }
        sb.append(key).append(": ").append(value.trim());
    }

    private String normalizeItemType(String rawType) {
        String type = rawType.toLowerCase();
        if (type.contains("ngh") || type.contains("art"))
            return "art";
        if (type.contains("dien") || type.contains("điện") || type.contains("electronic"))
            return "electronics";
        if (type.contains("phuong") || type.contains("phương") || type.contains("vehicle"))
            return "vehicle";
        return type;
    }

    private String getOptionalText(Map<String, Object> request, String key) {
        Object value = request.get(key);
        if (value == null)
            return null;
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private boolean getOptionalBoolean(Map<String, Object> request, String key) {
        Object value = request.get(key);
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private String handleEndAuction(Map<String, Object> request) {
        String sellerId = getRequiredText(request, "sellerId");
        int auctionId = getRequiredInt(request, "auctionId");
        Auction auction = auctionService.endAuctionBySeller(sellerId, auctionId);
        String response = JsonUtils.toJson(Map.of("status", "success", "auctionId", auction.getAuctionId(),
                "auctionStatus", String.valueOf(auction.getStatus())));
        broadcastPush("AUCTION_ENDED", auction);
        return response;
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
        Object rawAdminId = request.get("adminId");
        int adminId = 0;
        if (rawAdminId != null) {
            try {
                adminId = rawAdminId instanceof Number n ? n.intValue()
                        : Integer.parseInt(String.valueOf(rawAdminId).trim());
            } catch (NumberFormatException ignored) {
            }
        }
        if (adminId != 0 && userId == adminId) {
            throw new IllegalArgumentException("Admin không thể tự ban chính mình.");
        }
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
        String response = JsonUtils.toJson(Map.of("status", "success"));
        broadcastPush("AUCTION_CANCELED", auction);
        return response;
    }

    private String handleSwitchRole(Map<String, Object> request) {
        int userId = getRequiredInt(request, "userId");
        String targetRole = getRequiredText(request, "targetRole");
        User user = userService.findById(userId).orElseThrow(() -> new IllegalArgumentException("Khong tim thay user"));
        User switchedUser = userService.switchRole(user, targetRole);
        return buildUserResponse(switchedUser, null);
    }

    // ===== GROUP 4: Bidding flows =====
    // place bid + push realtime + query auctions/bid history
    private String handlePlaceBid(Map<String, Object> request) {
        // 1. Xác thực token — lấy userId thật từ JWT
        String token = getRequiredText(request, "token");
        User user = authService.authenticate(token);
        if (!(user instanceof Bidder bidder))
            throw new AuthenticationException("User khong phai bidder");

        // 2. Lấy dữ liệu từ request — KHÔNG tin bidderId, dùng userId từ token
        String auctionId = getRequiredText(request, "auctionId");
        double amount = getRequiredDouble(request, "amount");

        // 3. Đặt giá — an toàn vì bidder đã được xác thực qua JWT
        BidTransaction bid = bidService.placeBid(auctionId, bidder, amount);
        String response = JsonUtils.toJson(Map.of("status", "success", "bidId", bid.getId()));
        broadcastBidPush(auctionId, bid);
        return response;
    }

    private void broadcastBidPush(String auctionId, BidTransaction bid) {
        AuctionDAO dao = new AuctionDAO();
        Auction auction = dao.findById(Integer.parseInt(auctionId)).orElse(null);
        if (auction == null)
            return;

        Map<String, Object> push = new LinkedHashMap<>();
        push.put("push", "BID_PLACED");
        push.put("auctionId", auctionId);
        push.put("currentPrice", auction.getCurrentHighestBid());
        push.put("bidderId", String.valueOf(bid.getBidderId()));
        push.put("auctionStatus", auction.getStatus() != null ? auction.getStatus().name() : "-");

        ConnectionManager.getInstance().broadcast(JsonUtils.toJson(push));
    }

    private void broadcastPush(String event, Auction auction) {
        Map<String, Object> push = new LinkedHashMap<>();
        push.put("push", event);
        push.put("auctionId", String.valueOf(auction.getAuctionId()));
        push.put("currentPrice", auction.getCurrentHighestBid());
        push.put("auctionStatus", auction.getStatus() != null ? auction.getStatus().name() : "-");

        ConnectionManager.getInstance().broadcast(JsonUtils.toJson(push));
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
        if (a == null)
            return buildError("Khong tim thay phien dau gia: " + auctionId);
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

    // ===== GROUP 5: User profile / wallet =====
    private String handleGetUserById(Map<String, Object> request) {
        int userId = getRequiredInt(request, "userId");
        Optional<User> opt = userService.findById(userId);
        if (opt.isEmpty())
            return buildError("Khong tim thay user: " + userId);
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
        if (opt.isEmpty())
            return buildError("Khong tim thay user");
        User u = opt.get();
        boolean success = false;
        UserDAO dao = new UserDAO();
        if (u instanceof Bidder b) {
            success = b.getWallet().deposit(amount);
            if (success)
                dao.update(b);
        } else if (u instanceof Seller s) {
            success = s.getWallet().deposit(amount);
            if (success)
                dao.update(s);
        } else {
            return buildError("Khong the nap tien cho user nay");
        }
        if (!success)
            return buildError("Nap tien khong thanh cong");
        return JsonUtils.toJson(Map.of("status", "success"));
    }

    // ===== GROUP 6: Auto-bid control =====
    // register/cancel/status cho auto-bid agent
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
        if (!cancelled)
            return buildError("Khong tim thay agent auto-bid");
        return JsonUtils.toJson(Map.of("status", "success", "message", "Da huy auto-bid"));
    }

    private String handleGetAutoBidStatus(Map<String, Object> request) {
        int bidderId = getRequiredInt(request, "bidderId");
        int auctionId = getRequiredInt(request, "auctionId");
        AutoBidManager manager = AutoBidManager.getInstance();
        boolean hasActive = manager.hasActiveAgent(bidderId, auctionId);
        AutoBidAgent agent = manager.getAgent(bidderId, auctionId);
        if (!hasActive || agent == null)
            return JsonUtils.toJson(Map.of("status", "success", "active", false));
        return JsonUtils.toJson(Map.of("status", "success", "active", true, "agentId", agent.getAgentId(), "maxBid",
                agent.getMaxBid(), "increment", agent.getIncrement()));
    }

    // ===== GROUP 7: Response builders & input validation helpers =====
    private String buildUserResponse(User user, String token) {
        LinkedHashMap<String, Object> response = new LinkedHashMap<>();
        response.put("status", "success");
        if (token != null && !token.isBlank())
            response.put("token", token);
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
        if (value == null)
            throw new IllegalArgumentException(key + " khong duoc de trong");
        String text = String.valueOf(value).trim();
        if (text.isEmpty())
            throw new IllegalArgumentException(key + " khong duoc de trong");
        return text;
    }

    private int getRequiredInt(Map<String, Object> request, String key) {
        Object value = request.get(key);
        if (value == null)
            throw new IllegalArgumentException(key + " khong duoc de trong");
        if (value instanceof Number n)
            return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + " phai la so nguyen");
        }
    }

    private double getRequiredDouble(Map<String, Object> request, String key) {
        Object value = request.get(key);
        if (value == null)
            throw new IllegalArgumentException(key + " khong duoc de trong");
        if (value instanceof Number n)
            return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + " phai la so");
        }
    }

    private String buildError(String message) {
        return JsonUtils.toJson(Map.of("status", "error", "message", message));
    }

    // ===== GROUP 8: Mapping domain -> response DTO =====
    private String toItemTypeDisplay(Item item) {
        if (item instanceof Art)
            return "Tác phẩm nghệ thuật";
        if (item instanceof Electronics)
            return "Điện tử";
        if (item instanceof Vehicle)
            return "Phương tiện";
        return item.getClass_SimpleName();
    }

    private Map<String, Object> toAuctionMap(Auction auction) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("auctionId", String.valueOf(auction.getAuctionId()));
        map.put("itemName", auction.getItem() != null ? auction.getItem().getName() : "-");
        map.put("itemType", auction.getItem() != null ? toItemTypeDisplay(auction.getItem()) : "-");
        map.put("description", auction.getItem() != null ? auction.getItem().getDescription() : "");
        map.put("startingPrice", auction.getItem() != null ? auction.getItem().getStartingPrice() : 0);
        map.put("currentPrice", auction.getCurrentHighestBid());
        map.put("currentLeaderId", auction.getCurrentLeaderId());
        map.put("sellerId", auction.getSellerId() != null ? auction.getSellerId() : "");
        map.put("sellerUsername", resolveSellerUsername(auction.getSellerId()));
        map.put("auctionStatus", auction.getStatus() != null ? auction.getStatus().name() : "-");
        map.put("startTime", auction.getStartTime() != null ? auction.getStartTime().toString() : "");
        map.put("endTime", auction.getEndTime() != null ? auction.getEndTime().toString() : "");
        map.put("imageUrl",
                auction.getItem() != null && auction.getItem().getImageUrl() != null ? auction.getItem().getImageUrl()
                        : "");
        if (auction.getItem() instanceof Vehicle vehicle) {
            map.put("mileage", vehicle.getMileage());
        }
        if (auction.getItem() instanceof Art art) {
            map.put("artist", art.getArtist());
        }
        if (auction.getItem() instanceof Electronics electronics) {
            map.put("warrantyPeriod", electronics.getWarrantyPeriod());
        }
        return map;
    }

    private String resolveSellerUsername(String sellerId) {
        if (sellerId == null || sellerId.isBlank()) {
            return "";
        }
        try {
            int sellerUserId = Integer.parseInt(sellerId.trim());
            return userService.findById(sellerUserId).map(User::getUsername).orElse(sellerId);
        } catch (NumberFormatException ex) {
            return sellerId;
        }
    }
}
