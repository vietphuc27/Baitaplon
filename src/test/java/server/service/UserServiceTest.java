package server.service;

import common.exceptions.AuthenticationException;
import common.models.user.Admin;
import common.models.user.Bidder;
import common.models.user.Seller;
import common.models.user.User;
import common.models.user.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import server.repository.UserDAO;
import server.util.PasswordUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class UserServiceTest {
    private InMemoryUserDAO userDAO;
    private UserService userService;

    @BeforeEach
    void setUp() {
        userDAO = new InMemoryUserDAO();
        userService = new UserService(userDAO);
    }

    @Test
    void registerCreatesUserWithHashedPassword() {
        User user = userService.register(1, "alice", "alice@example.com", "secret", "bidder");

        assertInstanceOf(Bidder.class, user);
        assertEquals(UserStatus.LOGOUT, user.getStatus());
        assertNotEquals("secret", user.getPassword());
        assertTrue(PasswordUtil.verify("secret", user.getPassword()));
        assertTrue(userDAO.findById(1).isPresent());
    }

    @Test
    void registerRejectsDuplicateUsername() {
        userDAO.save(new Bidder(10, "alice", "a1@example.com", PasswordUtil.hash("x")));
        assertThrows(
                AuthenticationException.class,
                () -> userService.register(11, "alice", "a2@example.com", "secret", "bidder"));
    }

    @Test
    void registerRejectsDuplicateEmail() {
        userDAO.save(new Bidder(10, "alice", "dup@example.com", PasswordUtil.hash("x")));
        assertThrows(
                AuthenticationException.class,
                () -> userService.register(11, "bob", "dup@example.com", "secret", "bidder"));
    }

    @Test
    void loginUpdatesStatusToLogin() {
        Bidder user = new Bidder(1, "alice", "alice@example.com", PasswordUtil.hash("secret"));
        user.setStatus(UserStatus.LOGOUT);
        userDAO.save(user);

        User loggedIn = userService.login("alice", "secret");

        assertEquals(UserStatus.LOGIN, loggedIn.getStatus());
        assertEquals(UserStatus.LOGIN, userDAO.findById(1).orElseThrow().getStatus());
    }

    @Test
    void loginRejectsBannedUser() {
        Bidder user = new Bidder(2, "bob", "bob@example.com", PasswordUtil.hash("secret"));
        user.setStatus(UserStatus.BANNED);
        userDAO.save(user);

        assertThrows(AuthenticationException.class, () -> userService.login("bob", "secret"));
    }

    @Test
    void logoutSetsStatusToLogout() {
        Bidder user = new Bidder(3, "charlie", "charlie@example.com", PasswordUtil.hash("secret"));
        user.setStatus(UserStatus.LOGIN);
        userDAO.save(user);

        userService.logout(3);

        assertEquals(UserStatus.LOGOUT, userDAO.findById(3).orElseThrow().getStatus());
    }

    @Test
    void switchRoleChangesRoleAndKeepsLogin() {
        Bidder user = new Bidder(4, "diana", "diana@example.com", PasswordUtil.hash("secret"));
        user.setStatus(UserStatus.LOGIN);
        userDAO.save(user);

        User switched = userService.switchRole(user, "seller");

        assertInstanceOf(Seller.class, switched);
        assertEquals(UserStatus.LOGIN, switched.getStatus());
        assertEquals("SELLER", userDAO.findById(4).orElseThrow().getRole());
    }

    @Test
    void banAndUnbanUserUpdateStatus() {
        Bidder user = new Bidder(5, "emma", "emma@example.com", PasswordUtil.hash("secret"));
        user.setStatus(UserStatus.LOGOUT);
        userDAO.save(user);

        User banned = userService.banUser(5);
        assertEquals(UserStatus.BANNED, banned.getStatus());

        User unbanned = userService.unbanUser(5);
        assertEquals(UserStatus.LOGOUT, unbanned.getStatus());
    }

    @Test
    void changePasswordUpdatesHash() {
        Bidder user = new Bidder(6, "frank", "frank@example.com", PasswordUtil.hash("old"));
        userDAO.save(user);

        User changed = userService.changePassword("frank", "old", "newSecret");

        assertTrue(PasswordUtil.verify("newSecret", changed.getPassword()));
        assertFalse(PasswordUtil.verify("old", changed.getPassword()));
    }

    @Test
    void changePasswordRejectsWrongOldPassword() {
        Bidder user = new Bidder(7, "grace", "grace@example.com", PasswordUtil.hash("old"));
        userDAO.save(user);

        assertThrows(
                AuthenticationException.class,
                () -> userService.changePassword("grace", "wrong", "newSecret"));
    }

    @Test
    void findByIdRejectsNonPositiveId() {
        assertThrows(AuthenticationException.class, () -> userService.findById(0));
    }

    @Test
    void getAllUsersReturnsSavedUsers() {
        userDAO.save(new Bidder(8, "hank", "hank@example.com", PasswordUtil.hash("x")));
        userDAO.save(new Admin(9, "ivy", "ivy@example.com", PasswordUtil.hash("y")));

        List<User> users = userService.getAllUsers();

        assertEquals(2, users.size());
    }


    @Test
    void findByIdReturnsEmptyWhenUserNotFound() {
        Optional<User> result = userService.findById(999);
        assertTrue(result.isEmpty());
    }

    @Test
    void findByIdReturnsUserWhenFound() {
        userDAO.save(new Bidder(100, "jack", "jack@example.com", PasswordUtil.hash("x")));

        Optional<User> result = userService.findById(100);

        assertTrue(result.isPresent());
        assertEquals("jack", result.get().getUsername());
    }

    @Test
    void getAllUsersReturnsEmptyListWhenNoUsers() {
        List<User> users = userService.getAllUsers();
        assertTrue(users.isEmpty());
    }


    private static final class InMemoryUserDAO extends UserDAO {
        private final Map<Integer, User> usersById = new HashMap<>();
        private final Map<String, Integer> idByUsername = new HashMap<>();
        private final Map<String, Integer> idByEmail = new HashMap<>();

        @Override
        public void save(User user) {
            put(user);
        }

        @Override
        public void update(User user) {
            put(user);
        }

        @Override
        public Optional<User> findById(int id) {
            return Optional.ofNullable(usersById.get(id));
        }

        @Override
        public Optional<User> findByUsername(String username) {
            Integer id = idByUsername.get(username);
            return id == null ? Optional.empty() : Optional.ofNullable(usersById.get(id));
        }

        @Override
        public Optional<User> findByEmail(String email) {
            Integer id = idByEmail.get(email);
            return id == null ? Optional.empty() : Optional.ofNullable(usersById.get(id));
        }

        @Override
        public boolean existsByUsername(String username) {
            return idByUsername.containsKey(username);
        }

        @Override
        public boolean existsByEmail(String email) {
            return idByEmail.containsKey(email);
        }

        @Override
        public List<User> findAll() {
            return List.copyOf(usersById.values());
        }

        @Override
        public void updateRoleAndStatus(User user, String role, String status) {
            User updated = createByRole(role, user.getId(), user.getUsername(), user.getEmail(), user.getPassword());
            updated.setStatus(UserStatus.valueOf(status));
            put(updated);
        }

        private void put(User user) {
            usersById.put(user.getId(), user);
            idByUsername.put(user.getUsername(), user.getId());
            idByEmail.put(user.getEmail(), user.getId());
        }

        private User createByRole(String role, int id, String username, String email, String password) {
            return switch (role.toUpperCase()) {
                case "BIDDER" -> new Bidder(id, username, email, password);
                case "SELLER" -> new Seller(id, username, email, password);
                case "ADMIN" -> new Admin(id, username, email, password);
                default -> throw new IllegalArgumentException("Unsupported role: " + role);
            };
        }
    }
}
