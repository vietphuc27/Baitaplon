package server.network;

import java.time.LocalDateTime;
import java.util.Map;

import common.exceptions.AuthenticationException;
import common.models.auction.Auction;
import common.models.auction.AutoBidAgent;
import common.models.auction.BidTransaction;
import common.models.user.Bidder;
import common.models.user.User;
import common.utils.JsonUtils;
import server.manager.AutoBidManager;
import server.service.AuctionService;
import server.service.AuthService;
import server.service.BidService;
import server.service.ItemService;
import server.service.UserService;

public class RequestHandler {
    // Bộ định tuyến (Router). Đọc xem client muốn gì (action="login" hay
    // action="place_bid"), sau đó gọi đúng Service để xử lý.
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
                case "login" -> handleLogin(request);
                case "register" -> handleRegister(request);
                case "create_auction" -> handleCreateAuction(request);
                case "end_auction" -> handleEndAuction(request);
                case "place_bid" -> handlePlaceBid(request);
                case "register_auto_bid" -> handleRegisterAutoBid(request);
                case "cancel_auto_bid" -> handleCancelAutoBid(request);
                case "get_auto_bid_status" -> handleGetAutoBidStatus(request);
                case "ping" -> "{\"status\":\"success\",\"message\":\"pong\"}";
                default -> buildError("Action không được hỗ trợ: " + action);
            };
        } catch (AuthenticationException | IllegalArgumentException e) {
            return buildError(e.getMessage());
        } catch (Exception e) {
            return buildError("Lỗi hệ thống: " + e.getMessage());
        }
    }

    public String handleLogin(Map<String, Object> request) {
        String username = getRequiredText(request, "username");
        String password = getRequiredText(request, "password");
        String token = authService.login(username, password);
        return "{\"status\":\"success\",\"token\":\"" + token + "\"}";
    }

    private String handleRegister(Map<String, Object> request) {
        String username = getRequiredText(request, "username");
        String email = getRequiredText(request, "email");
        String password = getRequiredText(request, "password");
        String role = getRequiredText(request, "role");

        User user = userService.register(username, email, password, role);
        return "{\"status\":\"success\",\"userId\":\"" + user.getId() + "\"}";
    }

    private String handleCreateAuction(Map<String, Object> request) {
        String sellerId = getRequiredText(request, "sellerId");
        int itemId = Integer.parseInt(getRequiredText(request, "itemId"));
        LocalDateTime startTime = LocalDateTime.parse(getRequiredText(request, "startTime"));
        LocalDateTime endTime = LocalDateTime.parse(getRequiredText(request, "endTime"));
        Auction auction = auctionService.createAuction(sellerId, itemId, startTime, endTime);
        return "{\"status\":\"success\",\"auctionId\":\"" + auction.getAuctionId() + "\"}";
    }

    private String handleEndAuction(Map<String, Object> request) {
        String sellerId = getRequiredText(request, "sellerId");
        int auctionId = Integer.parseInt(getRequiredText(request, "auctionId"));

        Auction auction = auctionService.endAuctionBySeller(sellerId, auctionId);
        return "{\"status\":\"success\",\"auctionId\":\"" + auction.getAuctionId() + "\",\"auctionStatus\":\""
                + auction.getStatus() + "\"}";
    }

    private String handleRegisterAutoBid(Map<String, Object> request) {
        int bidderId = Integer.parseInt(getRequiredText(request, "bidderId"));
        int auctionId = Integer.parseInt(getRequiredText(request, "auctionId"));
        double maxBid = Double.parseDouble(getRequiredText(request, "maxBid"));
        double increment = Double.parseDouble(getRequiredText(request, "increment"));

        AutoBidManager manager = AutoBidManager.getInstance();
        int agentId = manager.registerAgent(bidderId, auctionId, maxBid, increment);

        if (agentId > 0) {
            return "{\"status\":\"success\",\"agentId\":\"" + agentId + "\"}";
        } else {
            return buildError("Thông số auto-bid không hợp lệ");
        }
    }

    private String handleCancelAutoBid(Map<String, Object> request) {
        int agentId = Integer.parseInt(getRequiredText(request, "agentId"));

        AutoBidManager manager = AutoBidManager.getInstance();
        boolean cancelled = manager.cancelAgent(agentId);

        if (cancelled) {
            return "{\"status\":\"success\",\"message\":\"Đã hủy auto-bid\"}";
        } else {
            return buildError("Không tìm thấy agent auto-bid");
        }
    }

    private String handleGetAutoBidStatus(Map<String, Object> request) {
        int bidderId = Integer.parseInt(getRequiredText(request, "bidderId"));
        int auctionId = Integer.parseInt(getRequiredText(request, "auctionId"));

        AutoBidManager manager = AutoBidManager.getInstance();
        boolean hasActive = manager.hasActiveAgent(bidderId, auctionId);
        AutoBidAgent agent = manager.getAgent(bidderId, auctionId);

        if (hasActive && agent != null) {
            return "{\"status\":\"success\",\"active\":true,"
                    + "\"agentId\":" + agent.getAgentId() + ","
                    + "\"maxBid\":" + agent.getMaxBid() + ","
                    + "\"increment\":" + agent.getIncrement() + "}";
        } else {
            return "{\"status\":\"success\",\"active\":false}";
        }
    }

    private String handlePlaceBid(Map<String, Object> request) {
        String auctionId = getRequiredText(request, "auctionId");
        int bidderId = Integer.parseInt(getRequiredText(request, "bidderId"));
        double amount = Double.parseDouble(getRequiredText(request, "amount"));

        User user = userService.findById(bidderId)
                .orElseThrow(() -> new IllegalArgumentException("Khong tim thay bidder"));

        if (!(user instanceof Bidder bidder)) {
            throw new IllegalArgumentException("User khong phai bidder");
        }

        BidTransaction bid = bidService.placeBid(auctionId, bidder, amount);
        return "{\"status\":\"success\",\"bidId\":\"" + bid.getId() + "\"}";
    }

    private String getRequiredText(Map<String, Object> request, String key) {
        Object value = request.get(key);
        if (value == null) {
            throw new IllegalArgumentException(key + " không được để trống");
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException(key + " không được để trống");
        }
        return text;
    }

    private String buildError(String message) {
        return "{\"status\":\"error\",\"message\":\"" + message + "\"}";
    }
}
