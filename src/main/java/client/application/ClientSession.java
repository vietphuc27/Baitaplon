package client.application;

import client.network.SocketClient;
import common.models.user.User;

public final class ClientSession {
    private static User currentUser;
    private static SocketClient sharedSocket;

    private ClientSession() {
    }

    public static void setCurrentUser(User user) {
        currentUser = user;
    }

    public static User getCurrentUser() {
        return currentUser;
    }

    public static SocketClient getSocket() {
        if (sharedSocket == null) {
            sharedSocket = new SocketClient();
            sharedSocket.connect();
        }
        return sharedSocket;
    }

    public static void clear() {
        currentUser = null;
        if (sharedSocket != null) {
            sharedSocket.close();
            sharedSocket = null;
        }
    }
}
