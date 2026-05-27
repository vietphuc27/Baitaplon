package server.manager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import server.network.ClientHandler;
import server.network.RequestHandler;

import java.net.Socket;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ConnectionManagerTest {

    @AfterEach
    public void tearDown() {
        ConnectionManager.getInstance().disconnectAll();
    }

    @Test
    public void addGetRemoveAndConnectedStatus() {
        ConnectionManager manager = ConnectionManager.getInstance();

        assertThrows(IllegalArgumentException.class, () -> manager.addClient(null));
        assertNull(manager.getClient(null));
        assertNull(manager.getClient("  "));
        assertFalse(manager.isConnected("missing"));

        FakeClientHandler c1 = new FakeClientHandler("c-1");
        FakeClientHandler c2 = new FakeClientHandler("c-2");

        manager.addClient(c1);
        manager.addClient(c2);

        assertEquals(2, manager.getClientCount());
        assertSame(c1, manager.getClient("c-1"));
        assertTrue(manager.isConnected("c-2"));

        List<String> ids = manager.getAllClientIds();
        assertTrue(ids.contains("c-1"));
        assertTrue(ids.contains("c-2"));

        manager.removeClient(null);
        manager.removeClient(" ");
        assertEquals(2, manager.getClientCount());

        manager.removeClient("c-1");
        assertEquals(1, manager.getClientCount());
        assertFalse(manager.isConnected("c-1"));
    }

    @Test
    public void broadcastAndDisconnectAllInvokeClientMethods() {
        ConnectionManager manager = ConnectionManager.getInstance();
        FakeClientHandler c1 = new FakeClientHandler("c-1");
        FakeClientHandler c2 = new FakeClientHandler("c-2");
        manager.addClient(c1);
        manager.addClient(c2);

        manager.broadcast("hello-all");

        assertEquals(1, c1.sendCount);
        assertEquals(1, c2.sendCount);
        assertEquals("hello-all", c1.lastMessage);
        assertEquals("hello-all", c2.lastMessage);

        manager.disconnectAll();

        assertEquals(1, c1.closeCount);
        assertEquals(1, c2.closeCount);
        assertEquals(0, manager.getClientCount());
    }

    private static final class FakeClientHandler extends ClientHandler {
        private final String id;
        private int sendCount;
        private int closeCount;
        private String lastMessage;

        private FakeClientHandler(String id) {
            super(new Socket(), new RequestHandler(), ConnectionManager.getInstance());
            this.id = id;
        }

        @Override
        public String getClientId() {
            return id;
        }

        @Override
        public void send(String message) {
            sendCount++;
            lastMessage = message;
        }

        @Override
        public void close() {
            closeCount++;
        }
    }
}
