package server.service;

import common.exceptions.AuthenticationException;
import common.models.user.Bidder;
import common.models.user.User;
import common.models.user.UserStatus;
import common.userfactory.AdminCreator;
import common.userfactory.BidderCreator;
import common.userfactory.SellerCreator;
import common.userfactory.UserFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import server.manager.SessionManager;
import server.repository.UserDAO;
import server.util.PasswordUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AuthServiceTest {
    private InMemoryUserDAO userDAO;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        // Load creator classes to register them with UserFactory
        new BidderCreator();
        new SellerCreator();
        new AdminCreator();
        userDAO = new InMemoryUserDAO();
        authService = new AuthService(userDAO);
        SessionManager.getInstance().logout();
    }

    @AfterEach
    void tearDown() {
        SessionManager.getInstance().logout();
    }

    @Test
    void constructorRejectsNullUserDao() {
        assertThrows(IllegalArgumentException.class, () -> new AuthService(null));
    }

    @Test
    void loginAcceptsValidCredentials() {
        Bidder user = createBidder(1, "alice", "alice@example.com", "secret");
        user.setStatus(UserStatus.LOGOUT);
        userDAO.put(user);

        Map<String, Object> result = authService.login("alice", "secret");

        assertNotNull(result);
        String accessToken = (String) result.get("accessToken");
        assertNotNull(accessToken);
        assertFalse(accessToken.isBlank());
        assertTrue(authService.validateToken(accessToken));
        assertEquals(UserStatus.LOGIN, userDAO.findById(1).orElseThrow().getStatus());
    }

    @Test
    void loginAcceptsTrimmedUsername() {
        Bidder user = createBidder(2, "diana", "diana@example.com", "secret");
        userDAO.put(user);

        Map<String, Object> result = authService.login("  diana  ", "secret");

        assertNotNull(result);
        String accessToken = (String) result.get("accessToken");
        assertNotNull(accessToken);
        assertTrue(authService.validateToken(accessToken));
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
    }

    @Test
    void loginRejectsBannedUser() {
        Bidder user = createBidder(4, "charlie", "charlie@example.com", "secret");
        user.setStatus(UserStatus.BANNED);
        userDAO.put(user);

        assertThrows(AuthenticationException.class, () -> authService.login("charlie", "secret"));
    }

    @Test
    void authenticateRejectsUnknownToken() {
        assertThrows(AuthenticationException.class, () -> authService.authenticate("unknown-token"));
    }

    @Test
    void validateTokenReturnsFalseForInvalidToken() {
        assertFalse(authService.validateToken(null));
        assertFalse(authService.validateToken(""));
        assertFalse(authService.validateToken("invalid.jwt.token"));
    }

    @Test
    void validateTokenReturnsTrueForValidToken() {
        Bidder user = createBidder(5, "eve", "eve@example.com", "secret");
        user.setStatus(UserStatus.LOGOUT);
        userDAO.put(user);

        Map<String, Object> result = authService.login("eve", "secret");
        String token = (String) result.get("accessToken");

        assertNotNull(token);
        assertTrue(authService.validateToken(token));
    }

    private Bidder createBidder(int id, String username, String email, String password) {
        Bidder bidder = (Bidder) UserFactory.createUser("bidder", id, username, email, password);
        bidder.setPassword(PasswordUtil.hash(password));
        return bidder;
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