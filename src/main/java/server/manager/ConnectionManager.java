package server.manager;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import server.network.ClientHandler;

public class ConnectionManager {
    //Quản lý các socket đang kết nối tới server.
     private final Map<String, ClientHandler> clientsById;

    public ConnectionManager() {
        this.clientsById = new ConcurrentHashMap<>();
    }

    public void addClient(ClientHandler clientHandler) {
        if (clientHandler == null) {
            throw new IllegalArgumentException("clientHandler không được null");
        }
        clientsById.put(clientHandler.getClientId(), clientHandler);
    }

    public void removeClient(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            return;
        }
        clientsById.remove(clientId);
    }

    public ClientHandler getClient(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            return null;
        }
        return clientsById.get(clientId);
    }

    public boolean isConnected(String clientId) {
        return getClient(clientId) != null;
    }

    public int getClientCount() {
        return clientsById.size();
    }

    public List<String> getAllClientIds() {
        return List.copyOf(clientsById.keySet());
    }

    public void broadcast(String message) {
        for (ClientHandler clientHandler : clientsById.values()) {
            clientHandler.send(message);
        }
    }

    public void disconnectAll() {
        for (ClientHandler clientHandler : clientsById.values()) {
            clientHandler.close();
        }
        clientsById.clear();
    }
}
