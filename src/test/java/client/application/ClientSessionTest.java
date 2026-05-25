package client.application;

import client.network.TestSocketClient;
import client.network.SocketClient;
import common.models.user.Bidder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientSessionTest {

    @AfterEach
    void tearDown() {
        ClientSession.clear();
    }

    @Test
    void setAndGetCurrentUserWork() {
        Bidder bidder = new Bidder(1, "u1", "u1@e", "p");
        ClientSession.setCurrentUser(bidder);
        assertEquals(bidder, ClientSession.getCurrentUser());
    }

    @Test
    void clearResetsUserAndClosesSharedSocket() throws Exception {
        TestSocketClient fakeSocket = new TestSocketClient();
        injectSharedSocket(fakeSocket);
        ClientSession.setCurrentUser(new Bidder(2, "u2", "u2@e", "p"));

        ClientSession.clear();

        assertNull(ClientSession.getCurrentUser());
        assertTrue(fakeSocket.wasClosedCalled());
    }

    @Test
    void getSocketIsLazyAndReturnsSingleton() {
        ClientSession.clear();
        SocketClient first = ClientSession.getSocket();
        SocketClient second = ClientSession.getSocket();
        assertSame(first, second);
        assertFalse(first.isConnected());
    }

    private void injectSharedSocket(TestSocketClient socketClient) throws Exception {
        Field field = ClientSession.class.getDeclaredField("sharedSocket");
        field.setAccessible(true);
        field.set(null, socketClient);
    }
}
