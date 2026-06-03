package client.network;

import common.utils.JsonUtils;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SocketClientServerTest {

    @Test
    void connectAndCloseLifecycleWorks() throws Exception {
        try (MockServer server = new MockServer((reader, writer) -> {
            Thread.sleep(300);
        }, JsonUtils.toJson(Map.of("status", "hello")))) {
            SocketClient client = new SocketClient("127.0.0.1", server.port());
            client.connect();
            assertTrue(client.isConnected());
            client.close();
            assertFalse(client.isConnected());
        }
    }

    @Test
    void pushListenerReceivesEventAfterConnect() throws Exception {
        CountDownLatch pushLatch = new CountDownLatch(1);
        AtomicReference<String> pushedEvent = new AtomicReference<>();

        try (MockServer server = new MockServer((reader, writer) -> {
            writer.println(JsonUtils.toJson(Map.of("push", "auction_updated", "auctionId", 9)));
            Thread.sleep(150);
        }, JsonUtils.toJson(Map.of("status", "hello")))) {
            SocketClient client = new SocketClient("127.0.0.1", server.port());
            client.setPushListener((event, data) -> {
                pushedEvent.set(event);
                pushLatch.countDown();
            });
            client.connect();
            assertTrue(pushLatch.await(2, TimeUnit.SECONDS));
            assertEquals("auction_updated", pushedEvent.get());
            client.close();
        }
    }

    @Test
    void dispatchPushSwallowsListenerException() throws Exception {
        SocketClient client = new SocketClient("127.0.0.1", 2026);
        client.setPushListener((event, data) -> {
            throw new RuntimeException("listener failed");
        });

        Method dispatchPush = SocketClient.class.getDeclaredMethod("dispatchPush", Map.class);
        dispatchPush.setAccessible(true);
        assertDoesNotThrow(() -> dispatchPush.invoke(client, Map.of("push", "boom", "x", 1)));
    }

    @Test
    void sendRequestAsyncWritesWithoutThrowing() throws Exception {
        CountDownLatch readLatch = new CountDownLatch(1);
        AtomicReference<String> action = new AtomicReference<>();

        try (MockServer server = new MockServer((reader, writer) -> {
            Map<?, ?> request = JsonUtils.fromJson(reader.readLine(), Map.class);
            action.set(String.valueOf(request.get("action")));
            readLatch.countDown();
        }, JsonUtils.toJson(Map.of("status", "hello")))) {
            SocketClient client = new SocketClient("127.0.0.1", server.port());
            client.sendRequestAsync("logout", null);
            assertTrue(readLatch.await(2, TimeUnit.SECONDS));
            assertEquals("logout", action.get());
            client.close();
        }
    }

    @Test
    void connectFailsWhenHandshakeIsBlank() throws Exception {
        try (MockServer server = new MockServer((reader, writer) -> {
        }, " ")) {
            SocketClient client = new SocketClient("127.0.0.1", server.port());
            assertThrows(RuntimeException.class, client::connect);
        }
    }

    @Test
    void requestAndAsyncHandleUnavailableServer() throws Exception {
        int unusedPort;
        try (ServerSocket temp = new ServerSocket(0)) {
            unusedPort = temp.getLocalPort();
        }
        SocketClient client = new SocketClient("127.0.0.1", unusedPort);
        assertThrows(RuntimeException.class, () -> client.sendRequest("ping", null));
        assertDoesNotThrow(() -> client.sendRequestAsync("logout", null));
    }

    @Test
    void sendRequestTimesOutWhenServerDoesNotReply() throws Exception {
        try (MockServer server = new MockServer((reader, writer) -> {
            reader.readLine();
            Thread.sleep(5200);
        }, JsonUtils.toJson(Map.of("status", "hello")))) {
            SocketClient client = new SocketClient("127.0.0.1", server.port());
            RuntimeException exception = assertThrows(RuntimeException.class,
                    () -> client.sendRequest("ping", Map.of("k", "v")));
            assertTrue(exception.getMessage().contains("không phản hồi")
                    || exception.getMessage().contains("khong phan hoi"));
            client.close();
        }
    }

    @FunctionalInterface
    private interface ClientHandler {
        void handle(BufferedReader reader, PrintWriter writer) throws Exception;
    }

    private static final class MockServer implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final Thread thread;

        MockServer(ClientHandler handler, String handshakeLine) throws IOException {
            this.serverSocket = new ServerSocket(0);
            this.thread = new Thread(() -> run(handler, handshakeLine), "mock-socket-server");
            this.thread.setDaemon(true);
            this.thread.start();
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        private void run(ClientHandler handler, String handshakeLine) {
            try (Socket socket = serverSocket.accept();
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                    PrintWriter writer = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8)) {
                writer.println(handshakeLine);
                handler.handle(reader, writer);
            } catch (Exception ignored) {
            }
        }

        @Override
        public void close() throws Exception {
            serverSocket.close();
            thread.join(1000);
        }
    }
}
