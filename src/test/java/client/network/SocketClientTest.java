package client.network;

import org.junit.jupiter.api.Test;

import java.net.ServerSocket;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class SocketClientTest {

    @Test
    void constructorValidatesHostAndPort() {
        assertThrows(IllegalArgumentException.class, () -> new SocketClient(null, 2026));
        assertThrows(IllegalArgumentException.class, () -> new SocketClient("   ", 2026));
        assertThrows(IllegalArgumentException.class, () -> new SocketClient("127.0.0.1", 0));
    }

    @Test
    void sendRequestAndAsyncValidateAction() {
        SocketClient client = new SocketClient("127.0.0.1", 2026);
        assertThrows(IllegalArgumentException.class, () -> client.sendRequest(" ", null));
        assertThrows(IllegalArgumentException.class, () -> client.sendRequestAsync(null, null));
    }

    @Test
    void isConnectedFalseByDefaultAndCloseSafe() {
        SocketClient client = new SocketClient("127.0.0.1", 2026);
        assertFalse(client.isConnected());
        client.close();
        assertFalse(client.isConnected());
    }

    @Test
    void sendRawRequestAndSetPushListenerEdgeCases() throws Exception {
        int unusedPort;
        try (ServerSocket temp = new ServerSocket(0)) {
            unusedPort = temp.getLocalPort();
        }
        SocketClient client = new SocketClient("127.0.0.1", unusedPort);
        assertThrows(RuntimeException.class, () -> client.sendRawRequest(java.util.Map.of("action", "test")));
        assertDoesNotThrow(() -> client.setPushListener(null));
        assertDoesNotThrow(() -> client.close());
    }
}
