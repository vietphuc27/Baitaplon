package server.network;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

import server.manager.ConnectionManager;

public class ClientHandler implements Runnable {
    // Bridge giua 1 TCP client va RequestHandler.
    // Nhiem vu: nhan request JSON, goi xu ly, gui response va don dep ket noi.
    private final Socket clientSocket;
    private final RequestHandler requestHandler;
    private final ConnectionManager connectionManager;
    private final Object sendLock = new Object();

    private PrintWriter writer;
    private BufferedReader reader;
    private String clientId;
    private Integer userId;
    private String authToken;
    private volatile boolean connected;
    private volatile boolean closed;

    public ClientHandler(Socket clientSocket, RequestHandler requestHandler, ConnectionManager connectionManager) {
        if (clientSocket == null) {
            throw new IllegalArgumentException("clientSocket không được null");
        }
        if (requestHandler == null) {
            throw new IllegalArgumentException("requestHandler không được null");
        }
        if (connectionManager == null) {
            throw new IllegalArgumentException("connectionManager không được null");
        }

        this.clientSocket = clientSocket;
        this.requestHandler = requestHandler;
        this.connectionManager = connectionManager;
    }

    // ===== GROUP 1: Connection lifecycle =====
    // Khoi tao stream IO, dang ky ket noi, gui handshake, sau do xu ly request loop.
    @Override
    public void run() {
        try {
            this.reader = new BufferedReader(
                    new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8)
            );
            this.writer = new PrintWriter(clientSocket.getOutputStream(), true, StandardCharsets.UTF_8);
            this.clientId = buildClientId();
            this.connected = true;

            connectionManager.addClient(this);
            send("{\"status\":\"connected\",\"clientId\":\"" + clientId + "\"}");

            String request;
            while (connected && (request = reader.readLine()) != null) {
                if (request.isBlank()) {
                    continue;
                }

                String response = requestHandler.handle(request, this);
                if (response != null && !response.isBlank()) {
                    send(response);
                }
            }
        } catch (SocketTimeoutException e) {
            logInfo("Client timeout: " + clientId);
        } catch (IOException e) {
            logError("Mat ket noi client " + clientId + ": " + e.getMessage());
        } catch (RuntimeException e) {
            logError("Loi xu ly client " + clientId + ": " + e.getMessage());
            sendSafeError();
        } finally {
            close();
        }
    }

    // ===== GROUP 2: Authentication context =====
    // Luu userId/token cho socket hien tai sau khi login thanh cong.
    public void markAuthenticated(Integer userId, String authToken) {
        this.userId = userId;
        this.authToken = authToken;
    }

    public synchronized void clearAuthentication() {
        this.userId = null;
        this.authToken = null;
    }

    // ===== GROUP 3: Outbound messaging =====
    // sendLock dam bao khong co 2 luong ghi chen vao cung output stream.
    public void send(String message) {
        if (!isConnected() || message == null || message.isBlank()) {
            return;
        }

        synchronized (sendLock) {
            writer.println(message);

            if (writer.checkError()) {
                logError("không thể gửi dữ liệu tới client: " + clientId);
                close();
            }
        }
    }

    // ===== GROUP 4: Accessors / state =====
    public String getClientId() {
        return clientId;
    }

    public Integer getUserId() {
        return userId;
    }

    public String getAuthToken() {
        return authToken;
    }

    public Socket getClientSocket() {
        return clientSocket;
    }

    public boolean isAuthenticated() {
        return userId != null && authToken != null && !authToken.isBlank();
    }

    public boolean isConnected() {
        return connected && !closed && !clientSocket.isClosed();
    }

    // ===== GROUP 5: Shutdown and cleanup =====
    // close() duoc goi o finally de chac chan thu hoi tai nguyen.
    public void close() {
        if (closed) {
            return;
        }

        closed = true;
        connected = false;

        if (clientId != null) {
            connectionManager.removeClient(clientId);
        }

        closeReader();
        closeWriter();
        closeSocket();
    }

    // Gui goi loi an toan neu co exception runtime trong vong xu ly request.
    private void sendSafeError() {
        try {
            send("{\"status\":\"error\",\"message\":\"Lỗi hệ thống\"}");
        } catch (RuntimeException ignored) {
        }
    }

    // Dong tung resource rieng de tranh leak khi ket noi bi ngat dot ngot.
    private void closeReader() {
        try {
            if (reader != null) {
                reader.close();
            }
        } catch (IOException e) {
            logError("Không thể đóng reader: " + e.getMessage());
        }
    }

    private void closeWriter() {
        if (writer != null) {
            writer.close();
        }
    }

    private void closeSocket() {
        try {
            if (!clientSocket.isClosed()) {
                clientSocket.close();
            }
        } catch (IOException e) {
            logError("Khong the dong client socket: " + e.getMessage());
        }
    }

    private String buildClientId() {
        return clientSocket.getInetAddress().getHostAddress() + ":" + clientSocket.getPort();
    }

    // ===== GROUP 6: Logging helpers =====
    private void logInfo(String message) {
        System.out.println("[ClientHandler] " + message);
    }

    private void logError(String message) {
        System.err.println("[ClientHandler] " + message);
    }
}
