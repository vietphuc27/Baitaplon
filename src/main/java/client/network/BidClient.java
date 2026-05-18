package client.network;

import common.models.auction.Auction;
import common.models.auction.AuctionStatus;
import common.models.auction.BidTransaction;
import common.models.item.Item;
import common.models.user.Bidder;

import java.time.LocalDateTime;
import java.util.*;

public class BidClient {
    private final SocketClient socketClient;

    public BidClient() {
        this(new SocketClient());
    }

    public BidClient(SocketClient socketClient) {
        if (socketClient == null) throw new IllegalArgumentException("socketClient khong duoc null");
        this.socketClient = socketClient;
    }

    public void refreshAuctionsStatus() {
        Map<String, Object> response = socketClient.sendRequest("refresh_auctions_status", null);
        ensureSuccess(response);
    }

    @SuppressWarnings("unchecked")
    public List<Auction> getAllAuctions() {
        Map<String, Object> response = socketClient.sendRequest("get_all_auctions", null);
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

    @SuppressWarnings("unchecked")
    public Optional<Auction> findAuctionById(int auctionId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("auctionId", String.valueOf(auctionId));
        Map<String, Object> response = socketClient.sendRequest("get_auction_by_id", payload);
        ensureSuccess(response);
        Auction a = mapToAuction(response);
        return a != null ? Optional.of(a) : Optional.empty();
    }

    public void placeBid(String auctionId, Bidder bidder, double amount) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("auctionId", auctionId);
        payload.put("bidderId", String.valueOf(bidder.getId()));
        payload.put("amount", amount);
        Map<String, Object> response = socketClient.sendRequest("place_bid", payload);
        ensureSuccess(response);
    }

    @SuppressWarnings("unchecked")
    public List<BidTransaction> getBidderBidHistory(String bidderId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("bidderId", bidderId);
        Map<String, Object> response = socketClient.sendRequest("get_bidder_bid_history", payload);
        ensureSuccess(response);
        return parseBidList(response);
    }

    @SuppressWarnings("unchecked")
    public List<BidTransaction> getAuctionBidHistory(String auctionId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("auctionId", auctionId);
        Map<String, Object> response = socketClient.sendRequest("get_auction_bid_history", payload);
        ensureSuccess(response);
        return parseBidList(response);
    }

    public int registerAutoBid(int bidderId, int auctionId, double maxBid, double increment) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("bidderId", String.valueOf(bidderId));
        payload.put("auctionId", String.valueOf(auctionId));
        payload.put("maxBid", maxBid);
        payload.put("increment", increment);
        Map<String, Object> response = socketClient.sendRequest("register_auto_bid", payload);
        ensureSuccess(response);
        Object agentId = response.get("agentId");
        return agentId instanceof Number n ? n.intValue() : -1;
    }

    public boolean cancelAutoBid(int agentId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("agentId", String.valueOf(agentId));
        Map<String, Object> response = socketClient.sendRequest("cancel_auto_bid", payload);
        return "success".equalsIgnoreCase(String.valueOf(response.get("status")));
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getAutoBidStatus(int bidderId, int auctionId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("bidderId", String.valueOf(bidderId));
        payload.put("auctionId", String.valueOf(auctionId));
        Map<String, Object> response = socketClient.sendRequest("get_auto_bid_status", payload);
        ensureSuccess(response);
        return response;
    }

    public void deposit(int userId, double amount) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", String.valueOf(userId));
        payload.put("amount", amount);
        Map<String, Object> response = socketClient.sendRequest("update_wallet_deposit", payload);
        ensureSuccess(response);
    }

    // ========== PARSE HELPERS ==========

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

            // Tao item fake de Item.getName() khong bi null
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

    @SuppressWarnings("unchecked")
    private List<BidTransaction> parseBidList(Map<String, Object> response) {
        List<Map<String, Object>> items = (List<Map<String, Object>>) response.get("bids");
        if (items == null) return List.of();
        List<BidTransaction> result = new ArrayList<>();
        for (Map<String, Object> m : items) {
            try {
                int bidId = m.get("bidId") instanceof Number n ? n.intValue() : 0;
                int auctionId = m.get("auctionId") instanceof Number n ? n.intValue() : 0;
                int bidderId = m.get("bidderId") instanceof Number n ? n.intValue() : 0;
                double amount = m.get("bidAmount") instanceof Number n ? n.doubleValue() : 0;
                String timeStr = (String) m.getOrDefault("bidTime", "");
                BidTransaction b = new BidTransaction(bidId, auctionId, bidderId, amount);
                if (!timeStr.isEmpty()) {
                    try { b.setBidTime(LocalDateTime.parse(timeStr)); } catch (Exception ignored) {}
                }
                result.add(b);
            } catch (Exception ignored) {}
        }
        return result;
    }

    private void ensureSuccess(Map<String, Object> response) {
        if (response == null || response.isEmpty()) throw new RuntimeException("Server khong tra ve du lieu");
        Object status = response.get("status");
        if (status == null || !"success".equalsIgnoreCase(String.valueOf(status))) {
            Object msg = response.get("message");
            throw new RuntimeException(msg == null ? "Yeu cau that bai" : String.valueOf(msg));
        }
    }
}
