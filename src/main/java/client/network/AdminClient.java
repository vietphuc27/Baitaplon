package client.network;

import common.models.auction.Auction;
import common.models.auction.AuctionStatus;
import common.models.item.Item;
import common.models.user.User;
import common.models.user.UserStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AdminClient {
    private final SocketClient socketClient;

    public AdminClient() {
        this(new SocketClient());
    }

    public AdminClient(SocketClient socketClient) {
        if (socketClient == null) throw new IllegalArgumentException("socketClient khong duoc null");
        this.socketClient = socketClient;
    }

    @SuppressWarnings("unchecked")
    public List<User> getAllUsers() {
        Map<String, Object> response = socketClient.sendRequest("get_all_users", null);
        ensureSuccess(response);
        List<Map<String, Object>> items = (List<Map<String, Object>>) response.get("users");
        if (items == null) return List.of();
        List<User> result = new ArrayList<>();
        for (Map<String, Object> m : items) {
            User u = mapToUser(m);
            if (u != null) result.add(u);
        }
        return result;
    }

    public void banUser(int userId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", String.valueOf(userId));
        Map<String, Object> response = socketClient.sendRequest("ban_user", payload);
        ensureSuccess(response);
    }

    public void unbanUser(int userId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", String.valueOf(userId));
        Map<String, Object> response = socketClient.sendRequest("unban_user", payload);
        ensureSuccess(response);
    }

    public void cancelAuction(int auctionId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("auctionId", String.valueOf(auctionId));
        Map<String, Object> response = socketClient.sendRequest("cancel_auction", payload);
        ensureSuccess(response);
    }

    // ─── PARSE HELPERS ─────────────────────────────────

    private User mapToUser(Map<String, Object> m) {
        try {
            int id = Integer.parseInt(String.valueOf(m.get("userId")));
            String username = (String) m.getOrDefault("username", "");
            String email = (String) m.getOrDefault("email", "");
            String role = (String) m.getOrDefault("role", "");
            String statusStr = (String) m.getOrDefault("userStatus", "LOGOUT");
            User user = new User(id, username, email, "", role) {};
            try { user.setStatus(UserStatus.valueOf(statusStr)); } catch (Exception ignored) {}
            return user;
        } catch (Exception e) {
            return null;
        }
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