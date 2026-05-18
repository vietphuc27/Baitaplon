package client.network;

import common.utils.JsonUtils;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public class SocketClient implements Closeable {
    private static final String DEFAULT_HOST = "127.0.0.1";
    private static final int DEFAULT_PORT = 2026;

    private final String host;
    private final int port;
    private final Object ioLock = new Object();

    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    private boolean connected;

    public SocketClient() {
        this(DEFAULT_HOST, DEFAULT_PORT);
    }

    public SocketClient(String host, int port) {
        if (host == null || host.trim().isEmpty()) {
            throw new IllegalArgumentException("host không được để trống");
        }
        if (port <= 0) {
            throw new IllegalArgumentException("port phải lớn hơn 0");
        }
        this.host = host.trim();
        this.port = port;
    }

    
// Mở kết nối tới server. Có thể gọi nhiều lần, chỉ kết nối thực sự 1 lần.
     
    public void connect() {
        synchronized (ioLock) {
            if (connected) {
                return;
            }
            doConnect();
            connected = true;
        }
    }

    public Map<String, Object> sendRequest(String action, Map<String, Object> payload) {
        if (action == null || action.trim().isEmpty()) {
            throw new IllegalArgumentException("action khong duoc de trong");
        }

        LinkedHashMap<String, Object> request = new LinkedHashMap<>();
        request.put("action", action.trim());
        if (payload != null) {
            request.putAll(payload);
        }
        return sendRawRequest(request);
    }

    public Map<String, Object> sendRawRequest(Map<String, Object> request) {
        synchronized (ioLock) {
            ensureConnected();
            try {
                writer.println(JsonUtils.toJson(request));
                writer.flush();

                String response = reader.readLine();
                if (response == null || response.isBlank()) {
                    closeQuietly();
                    connected = false;
                    throw new RuntimeException("Khong the doc response tu server: Connection reset");
                }
                return JsonUtils.fromJson(response, Map.class);
            } catch (IOException e) {
                closeQuietly();
                connected = false;
                throw new RuntimeException("Khong the ket noi toi server " + host + ":" + port + ": " + e.getMessage(), e);
            }
        }
    }

    public boolean isConnected() {
        synchronized (ioLock) {
            return connected && socket != null && socket.isConnected() && !socket.isClosed();
        }
    }

    @Override
    public void close() {
        synchronized (ioLock) {
            closeQuietly();
            connected = false;
        }
    }

    private void ensureConnected() {
        if (connected && socket != null && socket.isConnected() && !socket.isClosed()) {
            return;
        }
        doConnect();
        connected = true;
    }

    private void doConnect() {
        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(host,port),3000);
            socket.setSoTimeout(5000);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            writer = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8);

            String handshake = reader.readLine();
            if (handshake == null || handshake.isBlank()) {
                throw new IOException("Khong nhan duoc handshake tu server");
            }
            JsonUtils.fromJson(handshake, Map.class);
        } catch (IOException e) {
            closeQuietly();
            throw new RuntimeException("Khong the ket noi toi server " + host + ":" + port + ": " + e.getMessage(), e);
        }
    }

    private void closeQuietly() {
        try {
            if (reader != null) {
                reader.close();
            }
        } catch (IOException ignored) {
        }
        if (writer != null) {
            writer.close();
        }
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {
        }
        reader = null;
        writer = null;
        socket = null;
    }
}