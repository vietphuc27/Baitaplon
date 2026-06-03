package server.application;

import org.junit.jupiter.api.Test;
import server.manager.ConnectionManager;

import java.net.Socket;
import java.net.ServerSocket;

import static org.junit.jupiter.api.Assertions.*;

public class AuctionServerTest {

    @Test
    void constructorRejectsInvalidPortOrThreadCount() {
        assertThrows(IllegalArgumentException.class, () -> new AuctionServer(0, 2));
        assertThrows(IllegalArgumentException.class, () -> new AuctionServer(2026, 0));
    }

    @Test
    void gettersAndStopBehaveAsExpected() {
        ConnectionManager.getInstance().disconnectAll();

        AuctionServer server = new AuctionServer(3030, 2);
        assertEquals(3030, server.getPort());
        assertFalse(server.isRunning());
        assertEquals(0, server.getConnectedClientCount());
        assertDoesNotThrow(server::stop);
    }

    @Test
    void startThrowsWhenAlreadyRunning() throws Exception {
        int port;
        try (ServerSocket probe = new ServerSocket(0)) {
            port = probe.getLocalPort();
        }
        AuctionServer server = new AuctionServer(port, 2);
        Thread serverThread = new Thread(server::start, "auction-server-running-check");
        try {
            serverThread.start();
            assertTrue(waitUntilRunning(server, 2000));
            assertThrows(IllegalStateException.class, server::start);
        } finally {
            server.stop();
            serverThread.join(2000);
        }
    }

    @Test
    void startAcceptsClientAndStopsCleanly() throws Exception {
        int port;
        try (ServerSocket probe = new ServerSocket(0)) {
            port = probe.getLocalPort();
        }

        AuctionServer server = new AuctionServer(port, 2);
        Thread serverThread = new Thread(server::start, "auction-server-test");
        try {
            serverThread.start();
            assertTrue(waitUntilRunning(server, 2000));

            try (Socket ignored = new Socket("127.0.0.1", port)) {
                Thread.sleep(100);
            }
        } finally {
            server.stop();
            serverThread.join(2000);
        }
        assertFalse(serverThread.isAlive());
        assertFalse(server.isRunning());
    }

    private boolean waitUntilRunning(AuctionServer server, long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (!server.isRunning() && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        return server.isRunning();
    }
}
