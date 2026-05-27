package client.network;

import common.models.user.Bidder;
import common.models.user.Seller;
import common.models.user.User;
import common.models.user.UserStatus;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthClientTest {

    @Test
    void constructorRejectsNullSocket() {
        assertThrows(IllegalArgumentException.class, () -> new AuthClient(null));
    }

    @Test
    void loginMapsBidderAndWallet() {
        TestSocketClient socket = new TestSocketClient();
        socket.setResponse("login", Map.of(
                "status", "success",
                "userId", 10,
                "username", "alice",
                "email", "alice@example.com",
                "password", "hashed",
                "role", "bidder",
                "userStatus", "logout",
                "walletBalance", 123.5));

        AuthClient client = new AuthClient(socket);
        User user = client.login(" alice ", " secret ");

        assertInstanceOf(Bidder.class, user);
        assertEquals(UserStatus.LOGOUT, user.getStatus());
        assertEquals(123.5, ((Bidder) user).getWallet().getBalance());
        assertEquals("alice", socket.getPayloadOf("login").get("username"));
    }

    @Test
    void registerRejectsBlankRole() {
        AuthClient client = new AuthClient(new TestSocketClient());
        assertThrows(IllegalArgumentException.class, () -> client.register("a", "a@e", "p", " "));
    }

    @Test
    void switchRoleMapsSeller() {
        TestSocketClient socket = new TestSocketClient();
        socket.setResponse("switch_role", Map.of(
                "status", "success",
                "userId", 5,
                "username", "s",
                "email", "s@example.com",
                "password", "hashed",
                "role", "seller",
                "userStatus", "LOGIN",
                "walletBalance", 66.0));

        AuthClient client = new AuthClient(socket);
        User switched = client.switchRole(5, "seller");

        assertInstanceOf(Seller.class, switched);
        assertEquals(UserStatus.LOGIN, switched.getStatus());
        assertEquals(66.0, ((Seller) switched).getWallet().getBalance());
    }

    @Test
    void getUserByIdReturnsEmptyOnFailure() {
        TestSocketClient socket = new TestSocketClient();
        socket.setResponse("get_user_by_id", Map.of("status", "error", "message", "not found"));
        AuthClient client = new AuthClient(socket);
        assertTrue(client.getUserById(1).isEmpty());
    }

    @Test
    void getUserByIdReturnsPresentOnSuccess() {
        TestSocketClient socket = new TestSocketClient();
        socket.setResponse("get_user_by_id", Map.of(
                "status", "success",
                "userId", 11,
                "username", "bob",
                "email", "bob@example.com",
                "password", "p",
                "role", "BIDDER"));
        AuthClient client = new AuthClient(socket);
        assertTrue(client.getUserById(11).isPresent());
    }

    @Test
    void loginRejectsUnsupportedRole() {
        TestSocketClient socket = new TestSocketClient();
        socket.setResponse("login", Map.of(
                "status", "success",
                "userId", 2,
                "username", "x",
                "email", "x@e",
                "password", "p",
                "role", "guest"));
        AuthClient client = new AuthClient(socket);
        assertThrows(RuntimeException.class, () -> client.login("x", "p"));
    }

    @Test
    void registerTrimsPayloadValues() {
        TestSocketClient socket = new TestSocketClient();
        socket.setResponse("register", Map.of(
                "status", "success",
                "userId", 20,
                "username", "newUser",
                "email", "new@example.com",
                "password", "hashed",
                "role", "BIDDER"));
        AuthClient client = new AuthClient(socket);

        User user = client.register("  newUser  ", "  new@example.com ", "  pw ", " bidder ");

        assertInstanceOf(Bidder.class, user);
        assertEquals("newUser", socket.getPayloadOf("register").get("username"));
        assertEquals("new@example.com", socket.getPayloadOf("register").get("email"));
        assertEquals("pw", socket.getPayloadOf("register").get("password"));
        assertEquals("bidder", socket.getPayloadOf("register").get("role"));
    }

    @Test
    void logoutUsesAsyncRequest() {
        TestSocketClient socket = new TestSocketClient();
        AuthClient client = new AuthClient(socket);
        client.logout();
        assertEquals(1, socket.getAsyncActions().size());
        assertEquals("logout", socket.getAsyncActions().getFirst());
    }
}
