package client.network;

import client.application.ClientSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ClientDefaultConstructorCoverageTest {

    @AfterEach
    void tearDown() {
        ClientSession.clear();
    }

    @Test
    void defaultConstructorsUseSharedSocket() throws Exception {
        TestSocketClient socket = new TestSocketClient();
        socket.setResponse("login", Map.of(
                "status", "success",
                "userId", 1,
                "username", "u",
                "email", "u@e",
                "password", "p",
                "role", "BIDDER"));
        socket.setResponse("get_all_users", Map.of("status", "success", "users", List.of()));
        socket.setResponse("refresh_auctions_status", Map.of("status", "success"));
        socket.setResponse("get_seller_auctions", Map.of("status", "success"));
        injectSharedSocket(socket);

        new AuthClient().login("u", "p");
        new AdminClient().getAllUsers();
        new BidClient().refreshAuctionsStatus();
        new SellerClient().getSellerAuctions("s1");

        assertEquals(List.of("login", "get_all_users", "refresh_auctions_status", "get_seller_auctions"),
                socket.getSyncActions());
    }

    @Test
    void defaultLogoutCallsAsync() throws Exception {
        TestSocketClient socket = new TestSocketClient();
        injectSharedSocket(socket);

        new AuthClient().logout();
        new AdminClient().logout();

        assertEquals(List.of("logout", "logout"), socket.getAsyncActions());
    }

    private void injectSharedSocket(TestSocketClient socketClient) throws Exception {
        Field field = ClientSession.class.getDeclaredField("sharedSocket");
        field.setAccessible(true);
        field.set(null, socketClient);
    }
}

