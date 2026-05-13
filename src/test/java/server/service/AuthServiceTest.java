package server.service;

import common.exceptions.AuthenticationException;
import common.models.user.Bidder;
import common.models.user.User;
import common.models.user.UserStatus;
import common.userfactory.BidderCreator;
import common.userfactory.UserFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import server.manager.SessionManager;
import server.repository.UserDAO;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AuthServiceTest {
    private InMemoryUserDAO userDAO;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        new BidderCreator();
        userDAO = new InMemoryUserDAO();
        authService = new AuthService(userDAO, 1);
        resetAuthState();
    }

    @AfterEach
    void tearDown() {
        resetAuthState();
    }

    @Test
    void constructorRejectsNullUserDao() {
        assertThrows(IllegalArgumentException.class, () -> new AuthService(null, 1));
    }

    @Test
    void constructorRejectsNonPositiveTtl() {
        assertThrows(IllegalArgumentException.class, () -> new AuthService(userDAO, 0));
    }

    @Test
    void loginAcceptsValidCredentials() {
        Bidder user = createBidder(1, "alice", "alice@example.com", "secret");
        user.setStatus(UserStatus.LOGOUT);
        userDAO.put(user);

        String token = authService.login("alice", "secret");

        assertNotNull(token);
        assertFalse(token.isBlank());
        assertTrue(authService.validateToken(token));
        assertEquals(UserStatus.LOGIN, userDAO.findById(1).orElseThrow().getStatus());
        assertEquals("alice", SessionManager.getInstance().getCurrentUser().getUsername());
    }

    @Test
    void loginAcceptsTrimmedUsername() {
        Bidder user = createBidder(2, "diana", "diana@example.com", "secret");
        userDAO.put(user);

        String token = authService.login("  diana  ", "secret");

        assertNotNull(token);
        assertTrue(authService.validateToken(token));
        assertEquals("diana", SessionManager.getInstance().getCurrentUser().getUsername());
    }

    @Test
    void loginRejectsNullUsername() {
        assertThrows(AuthenticationException.class, () -> authService.login(null, "secret"));
    }

    @Test
    void loginRejectsBlankUsername() {
        assertThrows(AuthenticationException.class, () -> authService.login("   ", "secret"));
    }

    @Test
    void loginRejectsNullPassword() {
        assertThrows(AuthenticationException.class, () -> authService.login("alice", null));
    }

    @Test
    void loginRejectsBlankPassword() {
        assertThrows(AuthenticationException.class, () -> authService.login("alice", "   "));
    }

    @Test
    void loginRejectsUnknownUsername() {
        assertThrows(AuthenticationException.class, () -> authService.login("missing-user", "secret"));
    }

    @Test
    void loginRejectsWrongPassword() {
        Bidder user = createBidder(3, "bob", "bob@example.com", "secret");
        userDAO.put(user);

        assertThrows(AuthenticationException.class, () -> authService.login("bob", "wrong"));
        assertNull(SessionManager.getInstance().getCurrentUser());
    }

    @Test
    void loginRejectsBannedUser() {
        Bidder user = createBidder(4, "charlie", "charlie@example.com", "secret");
        user.setStatus(UserStatus.BANNED);
        userDAO.put(user);

        assertThrows(AuthenticationException.class, () -> authService.login("charlie", "secret"));
        assertNull(SessionManager.getInstance().getCurrentUser());
    }

    @Test
    void authenticateRejectsUnknownToken() {
        assertThrows(AuthenticationException.class, () -> authService.authenticate("unknown-token"));
    }

    private void resetAuthState() {
        try {
            Field tokenStoreField = AuthService.class.getDeclaredField("TOKEN_STORE");
            tokenStoreField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, ?> tokenStore = (Map<String, ?>) tokenStoreField.get(null);
            tokenStore.clear();

            SessionManager.getInstance().logout();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot reset auth state", e);
        }
    }

    private Bidder createBidder(int id, String username, String email, String password) {
        return (Bidder) UserFactory.createUser("bidder", id, username, email, password);
    }

    private static final class InMemoryUserDAO extends UserDAO {
        private final Map<Integer, User> users = new HashMap<>();
        private final Map<String, User> byUsername = new HashMap<>();

        void put(User user) {
            users.put(user.getId(), user);
            byUsername.put(user.getUsername(), user);
        }

        @Override
        public Optional<User> findByUsername(String username) {
            return Optional.ofNullable(byUsername.get(username));
        }

        @Override
        public Optional<User> findById(int id) {
            return Optional.ofNullable(users.get(id));
        }

        @Override
        public void update(User user) {
            put(user);
        }
    }
}
