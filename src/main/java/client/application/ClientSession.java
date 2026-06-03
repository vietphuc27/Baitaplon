package client.application;

import client.network.SocketClient;
import common.models.user.User;

public final class ClientSession {
    private static User currentUser;
    private static SocketClient sharedSocket;
    private static String authToken;    // JWT access token (ngắn hạn: 15 phút)
    private static String refreshToken; // Refresh token (dài hạn: 7 ngày)

    private ClientSession() {
    }

    public static void setCurrentUser(User user) {
        currentUser = user;
    }

    public static User getCurrentUser() {
        return currentUser;
    }

    public static String getAuthToken() {
        return authToken;
    }

    public static void setAuthToken(String token) {
        authToken = token;
    }

    public static String getRefreshToken() {
        return refreshToken;
    }

    public static void setRefreshToken(String token) {
        refreshToken = token;
    }

    public static SocketClient getSocket() {
        if (sharedSocket == null) {
            sharedSocket = new SocketClient();
        }
        return sharedSocket;
    }

    public static void clear() {
        currentUser = null;
        authToken = null;
        refreshToken = null;
        if (sharedSocket != null) {
            sharedSocket.close();
            sharedSocket = null;
        }
    }
}
