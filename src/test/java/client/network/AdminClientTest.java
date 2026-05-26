package client.network;

import common.models.user.User;
import common.models.user.UserStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AdminClientTest {

    @Test
    void constructorRejectsNullSocket() {
        assertThrows(IllegalArgumentException.class, () -> new AdminClient(null));
    }

    @Test
    void getAllUsersParsesValidAndSkipsInvalid() {
        TestSocketClient socket = new TestSocketClient();
        socket.setResponse("get_all_users", Map.of(
                "status", "success",
                "users", List.of(
                        Map.of("userId", 1, "username", "u1", "email", "u1@e", "role", "ADMIN", "userStatus", "LOGIN"),
                        Map.of("username", "broken"))));

        AdminClient client = new AdminClient(socket);
        List<User> users = client.getAllUsers();

        assertEquals(1, users.size());
        assertEquals("u1", users.getFirst().getUsername());
        assertEquals(UserStatus.LOGIN, users.getFirst().getStatus());
    }

    @Test
    void banUserThrowsWhenServerReturnsError() {
        TestSocketClient socket = new TestSocketClient();
        socket.setResponse("ban_user", Map.of("status", "error", "message", "fail"));
        AdminClient client = new AdminClient(socket);
        assertThrows(RuntimeException.class, () -> client.banUser(10));
    }

    @Test
    void getAllUsersReturnsEmptyWhenServerOmitsUsersList() {
        TestSocketClient socket = new TestSocketClient();
        socket.setResponse("get_all_users", Map.of("status", "success"));
        AdminClient client = new AdminClient(socket);
        assertTrue(client.getAllUsers().isEmpty());
    }

    @Test
    void getAllUsersKeepsDefaultStatusWhenStatusTextInvalid() {
        TestSocketClient socket = new TestSocketClient();
        socket.setResponse("get_all_users", Map.of(
                "status", "success",
                "users", List.of(Map.of(
                        "userId", 4,
                        "username", "u4",
                        "email", "u4@e",
                        "role", "ADMIN",
                        "userStatus", "NOT_A_REAL_STATUS"))));
        AdminClient client = new AdminClient(socket);
        User user = client.getAllUsers().getFirst();
        assertEquals(UserStatus.LOGIN, user.getStatus());
    }

    @Test
    void unbanAndCancelAuctionServerErrorPaths() {
        TestSocketClient socket = new TestSocketClient();
        socket.setResponse("unban_user", Map.of("status", "error", "message", "no user"));
        socket.setResponse("cancel_auction", Map.of("status", "error"));

        AdminClient client = new AdminClient(socket);
        assertThrows(RuntimeException.class, () -> client.unbanUser(9));
        assertThrows(RuntimeException.class, () -> client.cancelAuction(77));
    }

    @Test
    void unbanAndCancelAuctionSendExpectedActions() {
        TestSocketClient socket = new TestSocketClient();
        socket.setResponse("unban_user", Map.of("status", "success"));
        socket.setResponse("cancel_auction", Map.of("status", "success"));
        AdminClient client = new AdminClient(socket);

        client.unbanUser(9);
        client.cancelAuction(77);
        client.logout();

        assertEquals(List.of("unban_user", "cancel_auction"), socket.getSyncActions());
        assertEquals("9", socket.getPayloadOf("unban_user").get("userId"));
        assertEquals("77", socket.getPayloadOf("cancel_auction").get("auctionId"));
        assertEquals(List.of("logout"), socket.getAsyncActions());
    }
}
