package client.network;

import common.models.user.Admin;
import common.models.user.Bidder;
import common.models.user.Seller;
import common.models.user.User;
import common.models.user.UserStatus;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public class AuthClient {
    private final SocketClient socketClient;

    public AuthClient() {
        this(new SocketClient());
    }

    public AuthClient(SocketClient socketClient) {
        if (socketClient == null) throw new IllegalArgumentException("socketClient khong duoc null");
        this.socketClient = socketClient;
    }

    public User login(String username, String password) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("username", requireText(username, "username"));
        payload.put("password", requireText(password, "password"));
        Map<String, Object> response = socketClient.sendRequest("login", payload);
        ensureSuccess(response);
        return toUser(response);
    }

    public User register(String username, String email, String password, String role) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("username", requireText(username, "username"));
        payload.put("email", requireText(email, "email"));
        payload.put("password", requireText(password, "password"));
        payload.put("role", requireText(role, "role"));
        Map<String, Object> response = socketClient.sendRequest("register", payload);
        ensureSuccess(response);
        return toUser(response);
    }

    public void logout() {
        socketClient.sendRequest("logout", null);
    }

    public User switchRole(int userId, String targetRole) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", userId);
        payload.put("targetRole", requireText(targetRole, "targetRole"));
        Map<String, Object> response = socketClient.sendRequest("switch_role", payload);
        ensureSuccess(response);
        return toUser(response);
    }

    public Optional<User> getUserById(int userId) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", userId);
        try {
            Map<String, Object> response = socketClient.sendRequest("get_user_by_id", payload);
            ensureSuccess(response);
            User user = toUser(response);
            return Optional.ofNullable(user);
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private User toUser(Map<String, Object> response) {
        int userId = readInt(response, "userId");
        String username = readText(response, "username");
        String email = readText(response, "email");
        String password = response.get("password") == null ? "" : String.valueOf(response.get("password"));
        String role = readText(response, "role").toUpperCase();

        User user = switch (role) {
            case "SELLER" -> new Seller(userId, username, email, password);
            case "BIDDER" -> new Bidder(userId, username, email, password);
            case "ADMIN" -> new Admin(userId, username, email, password);
            default -> throw new RuntimeException("Role khong duoc ho tro: " + role);
        };

        Object statusValue = response.get("userStatus");
        if (statusValue != null) {
            user.setStatus(UserStatus.valueOf(String.valueOf(statusValue).trim().toUpperCase()));
        }

        Object walletBalance = response.get("walletBalance");
        if (walletBalance instanceof Number number) {
            if (user instanceof Bidder bidder) {
                bidder.getWallet().setBalance(number.doubleValue());
            } else if (user instanceof Seller seller) {
                seller.getWallet().setBalance(number.doubleValue());
            }
        }

        return user;
    }

    private void ensureSuccess(Map<String, Object> response) {
        if (response == null || response.isEmpty()) throw new RuntimeException("Server khong tra ve du lieu");
        Object status = response.get("status");
        if (status == null || !"success".equalsIgnoreCase(String.valueOf(status))) {
            Object message = response.get("message");
            throw new RuntimeException(message == null ? "Yeu cau that bai" : String.valueOf(message));
        }
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(fieldName + " khong duoc de trong");
        return value.trim();
    }

    private String readText(Map<String, Object> response, String key) {
        Object value = response.get(key);
        if (value == null || String.valueOf(value).trim().isEmpty()) throw new RuntimeException("Server thieu truong " + key);
        return String.valueOf(value).trim();
    }

    private int readInt(Map<String, Object> response, String key) {
        Object value = response.get(key);
        if (value == null) throw new RuntimeException("Server thieu truong " + key);
        if (value instanceof Number number) return number.intValue();
        try { return Integer.parseInt(String.valueOf(value).trim()); }
        catch (NumberFormatException e) { throw new RuntimeException("Gia tri " + key + " khong hop le"); }
    }
}