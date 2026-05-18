package client.network;

import common.models.auction.Auction;
import common.models.auction.AuctionStatus;
import common.models.item.Item;
import common.utils.JsonUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class SellerClient {
    private final SocketClient socketClient;

    public SellerClient() {
        this(new SocketClient());
    }

    public SellerClient(SocketClient socketClient) {
        if (socketClient == null) throw new IllegalArgumentException("socketClient khong duoc null");
        this.socketClient = socketClient;
    }

    @SuppressWarnings("unchecked")
    public List<Auction> getSellerAuctions(String sellerId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("sellerId", sellerId);
        Map<String, Object> response = socketClient.sendRequest("get_seller_auctions", payload);
        ensureSuccess(response);
        List<Map<String, Object>> items = (List<Map<String, Object>>) response.get("auctions");
        if (items == null) return List.of();
        List<Auction> result = new ArrayList<>();
        for (Map<String, Object> m : items) {
            Auction a = mapToAuction(m);
            if (a != null) result.add(a);
        }
        return result;
    }

    public Auction createAuction(CreateAuctionRequest request) {
        validateRequest(request);
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("sellerId", request.sellerId());
        payload.put("itemName", request.itemName().trim());
        payload.put("itemType", request.itemType().trim());
        payload.put("startPrice", request.startPrice());
        payload.put("description", request.description().trim());
        payload.put("endTime", request.endTime().toString());
        putIfPresent(payload, "artist", request.artist());
        putIfPresent(payload, "artYear", request.artYear());
        putIfPresent(payload, "material", request.material());
        putIfPresent(payload, "brand", request.brand());
        putIfPresent(payload, "model", request.model());
        putIfPresent(payload, "condition", request.condition());
        putIfPresent(payload, "vehicleBrand", request.vehicleBrand());
        putIfPresent(payload, "mileage", request.mileage());
        putIfPresent(payload, "vehicleYear", request.vehicleYear());

        Map<String, Object> response = socketClient.sendRequest("create_auction", payload);
        ensureSuccess(response);
        // Tra ve auction info tu response
        Object auctionIdObj = response.get("auctionId");
        if (auctionIdObj == null) throw new RuntimeException("Server khong tra ve auctionId");
        int auctionId = auctionIdObj instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(auctionIdObj));
        // Tao auction minimal de tra ve
        Item item = new Item(0, request.itemName(), request.description(), request.startPrice(), request.sellerId()) {
            @Override public String getInfo() { return request.itemName(); }
        };
        Auction auction = new Auction(auctionId, item, request.sellerId(), LocalDateTime.now(), request.endTime());
        return auction;
    }

    public void endAuction(String sellerId, String auctionId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("sellerId", sellerId);
        payload.put("auctionId", auctionId);
        Map<String, Object> response = socketClient.sendRequest("end_auction", payload);
        ensureSuccess(response);
    }

    // ========== HELPERS ==========

    private void putIfPresent(LinkedHashMap<String, Object> map, String key, String value) {
        if (value != null && !value.trim().isEmpty()) {
            map.put(key, value.trim());
        }
    }

    private Auction mapToAuction(Map<String, Object> m) {
        try {
            int id = Integer.parseInt(String.valueOf(m.get("auctionId")));
            String itemName = (String) m.getOrDefault("itemName", "-");
            String sellerId = (String) m.getOrDefault("sellerId", "");
            String statusStr = String.valueOf(m.getOrDefault("auctionStatus", m.getOrDefault("status", "-")));
            double currentPrice = m.get("currentPrice") instanceof Number n ? n.doubleValue() : 0;
            String startTimeStr = (String) m.getOrDefault("startTime", "");
            String endTimeStr = (String) m.getOrDefault("endTime", "");

            LocalDateTime startTime = startTimeStr.isEmpty() ? null : LocalDateTime.parse(startTimeStr);
            LocalDateTime endTime = endTimeStr.isEmpty() ? null : LocalDateTime.parse(endTimeStr);

            Item item = new Item(0, itemName, "", currentPrice, sellerId) {
                @Override public String getInfo() { return itemName; }
            };

            Auction a = new Auction(id, item, sellerId, startTime, endTime);
            a.setCurrentHighestBid(currentPrice);
            if (!statusStr.equals("-")) {
                try { a.setStatus(AuctionStatus.valueOf(statusStr)); } catch (Exception ignored) {}
            }
            return a;
        } catch (Exception e) {
            return null;
        }
    }

    private void validateRequest(CreateAuctionRequest request) {
        if (request == null) throw new IllegalArgumentException("Thong tin tao auction khong duoc de trong");
        requireText(request.sellerId(), "sellerId");
        requireText(request.itemName(), "itemName");
        requireText(request.itemType(), "itemType");
        requireText(request.description(), "description");
        if (request.startPrice() <= 0) throw new IllegalArgumentException("Gia khoi diem phai lon hon 0");
        if (request.endTime() == null) throw new IllegalArgumentException("Thoi gian ket thuc khong duoc de trong");
        if (!LocalDateTime.now().isBefore(request.endTime()))
            throw new IllegalArgumentException("Thoi gian ket thuc phai sau hien tai");
    }

    private void ensureSuccess(Map<String, Object> response) {
        if (response == null || response.isEmpty()) throw new RuntimeException("Server khong tra ve du lieu");
        Object status = response.get("status");
        if (status == null || !"success".equalsIgnoreCase(String.valueOf(status))) {
            Object msg = response.get("message");
            throw new RuntimeException(msg == null ? "Yeu cau that bai" : String.valueOf(msg));
        }
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty())
            throw new IllegalArgumentException(fieldName + " khong duoc de trong");
        return value.trim();
    }

    public record CreateAuctionRequest(
            String sellerId,
            String itemName,
            String itemType,
            double startPrice,
            String description,
            LocalDateTime endTime,
            String artist,
            String artYear,
            String material,
            String brand,
            String model,
            String condition,
            String vehicleBrand,
            String mileage,
            String vehicleYear
    ) {}
}