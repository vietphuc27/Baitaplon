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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class SocketClient implements Closeable {

    /** Interface để controller nhận push message từ server */
    public interface PushListener {
        void onPush(String event, Map<String, Object> data);
    }

    private static final String DEFAULT_HOST = "127.0.0.1";
    private static final int DEFAULT_PORT = 2026;
    private static final long RESPONSE_TIMEOUT_MS = 30_000;

    private final String host;
    private final int port;
    private final Object ioLock = new Object();
    private final Object listenerLock = new Object();
    private final BlockingQueue<Map<String, Object>> responseQueue = new LinkedBlockingQueue<>();

    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    private volatile boolean connected;
    private volatile boolean running;
    private Thread listenerThread;
    private PushListener pushListener;

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

    /** Đăng ký listener nhận push từ server */
    public void setPushListener(PushListener listener) {
        synchronized (listenerLock) {
            this.pushListener = listener;
        }
    }

    /**
     * Mở kết nối tới server. Có thể gọi nhiều lần, chỉ kết nối thực sự 1 lần.
     */
    public void connect() {
        synchronized (ioLock) {
            if (connected) {
                return;
            }
            doConnect();
            connected = true;
            running = true;
            startListenerThread();
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
        // Gửi request
        synchronized (ioLock) {
            ensureConnected();
            try {
                writer.println(JsonUtils.toJson(request));
                writer.flush();
            } catch (RuntimeException e) {
                closeQuietly();
                connected = false;
                throw new RuntimeException("Không thể gửi request tới server: " + e.getMessage(), e);
            }
        }

        // Chờ response từ thread nền
        try {
            Map<String, Object> response = responseQueue.poll(RESPONSE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (response == null) {
                closeQuietly();
                connected = false;
                throw new RuntimeException("Server không phản hồi trong thời gian chờ");
            }
            return response;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Bị gián đoạn khi chờ phản hồi từ server", e);
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
            running = false;
            if (listenerThread != null) {
                listenerThread.interrupt();
                listenerThread = null;
            }
            closeQuietly();
            connected = false;
        }
    }

    // ─── private ────────────────────────────────────────────────

    private void ensureConnected() {
        if (connected && socket != null && socket.isConnected() && !socket.isClosed()) {
            return;
        }
        doConnect();
        connected = true;
        running = true;
        startListenerThread();
    }

    private void doConnect() {
        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), 3000);
            socket.setSoTimeout(0);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            writer = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8);

            // Đọc handshake đầu tiên
            String handshake = reader.readLine();
            if (handshake == null || handshake.isBlank()) {
                throw new IOException("Không nhận được handshake từ server");
            }
            JsonUtils.fromJson(handshake, Map.class);
        } catch (IOException e) {
            closeQuietly();
            throw new RuntimeException("Không thể kết nối tới server " + host + ":" + port + ": " + e.getMessage(), e);
        }
    }

    private void startListenerThread() {
        if (listenerThread != null && listenerThread.isAlive()) {
            return;
        }
        listenerThread = new Thread(this::listenLoop, "socket-listener");
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    /** Thread nền đọc tất cả message từ socket, phân loại push vs response */
    private void listenLoop() {
        while (running && connected) {
            try {
                String line = reader.readLine();
                if (line == null || line.isBlank()) {
                    break; // server đóng kết nối
                }

                Map<String, Object> message = JsonUtils.fromJson(line, Map.class);
                if (message == null) {
                    continue;
                }

                if (message.containsKey("push")) {
                    // Push message → dispatch cho listener
                    dispatchPush(message);
                } else {
                    // Response thường → đưa vào queue cho sendRequest
                    responseQueue.offer(message);
                }
            } catch (IOException e) {
                break;
            } catch (RuntimeException e) {
                // Lỗi parse JSON, bỏ qua
            }
        }

        synchronized (ioLock) {
            connected = false;
            closeQuietly();
        }
    }

    @SuppressWarnings("unchecked")
    private void dispatchPush(Map<String, Object> message) {
        PushListener listener;
        synchronized (listenerLock) {
            listener = this.pushListener;
        }
        if (listener == null) {
            return;
        }

        String event = String.valueOf(message.getOrDefault("push", ""));
        try {
            listener.onPush(event, message);
        } catch (RuntimeException e) {
            System.err.println("[SocketClient] Lỗi xử lý push event '" + event + "': " + e.getMessage());
        }
    }

    private void closeQuietly() {
        responseQueue.clear();
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