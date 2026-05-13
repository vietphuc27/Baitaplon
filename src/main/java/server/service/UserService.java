package server.service;

import common.exceptions.AuthenticationException;
import common.models.user.User;
import common.models.user.UserStatus;
import common.userfactory.UserFactory;
import server.manager.SessionManager;
import server.repository.UserDAO;
import server.util.PasswordUtil;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

public class UserService {
    private static final String[] USER_CREATOR_CLASSES = {
            "common.userfactory.SellerCreator",
            "common.userfactory.BidderCreator",
            "common.userfactory.AdminCreator"
    };

    private final SessionManager sessionManager;
    private final UserDAO userDAO;

    public UserService() {
        this(new UserDAO());
    }

    public UserService(UserDAO userDAO) {
        this.userDAO = userDAO;
        this.sessionManager = SessionManager.getInstance();
        ensureUserCreatorsLoaded();
    }

    public User register(int id, String username, String email, String password, String role) {
        int normalizedId = requirePositiveId(id, "id");
        String normalizedUsername = requireText(username, "username");
        String normalizedEmail = requireText(email, "email");
        String normalizedPassword = requireText(password, "password");
        String normalizedRole = normalizeRole(role);

        if (userDAO.existsByUsername(normalizedUsername)) {
            throw new AuthenticationException("Username đã tồn tại");
        }

        if (userDAO.existsByEmail(normalizedEmail)) {
            throw new AuthenticationException("Email đã tồn tại");
        }

        String hashedPassword = PasswordUtil.hash(normalizedPassword);

        User user = UserFactory.createUser(
                normalizedRole,
                normalizedId,
                normalizedUsername,
                normalizedEmail,
                hashedPassword);

        user.setStatus(UserStatus.LOGOUT);
        userDAO.save(user);
        return user;
    }

    public User register(String username, String email, String password, String role) {
        return register(generateUserId(), username, email, password, role);
    }

    public User login(String username, String password) {
        String normalizedUsername = requireText(username, "username");
        String normalizedPassword = requireText(password, "password");

        User user = userDAO.findByUsername(normalizedUsername)
                .orElseThrow(() -> new AuthenticationException("Không tìm thấy username"));

        if (user.getStatus() == UserStatus.BANNED) {
            throw new AuthenticationException("Tài khoản đã bị khóa");
        }

        boolean passwordMatches = false;
        try {
            passwordMatches = PasswordUtil.verify(normalizedPassword, user.getPassword());
        } catch (Exception e) {
            passwordMatches = user.getPassword().equals(normalizedPassword);
        }

        if (!passwordMatches) {
            throw new AuthenticationException("Sai mật khẩu");
        }

        user.setStatus(UserStatus.LOGIN);
        userDAO.update(user);
        sessionManager.login(user);
        return user;
    }

    public void logout() {
        User currentUser = sessionManager.getCurrentUser();
        if (currentUser == null) {
            return;
        }

        currentUser.setStatus(UserStatus.LOGOUT);
        userDAO.update(currentUser);
        sessionManager.logout();
    }

    public User banUser(int id) {
        User user = getRequiredUserById(id);
        user.setStatus(UserStatus.BANNED);
        userDAO.update(user);

        User currentUser = sessionManager.getCurrentUser();
        if (currentUser != null && currentUser.getId() == user.getId()) {
            sessionManager.logout();
        }

        return user;
    }

    public User unbanUser(int id) {
        User user = getRequiredUserById(id);
        user.setStatus(UserStatus.LOGOUT);
        userDAO.update(user);
        return user;
    }

    public User changePassword(String username, String oldPassword, String newPassword) {
        String normalizedUsername = requireText(username, "username");
        String normalizedOldPassword = requireText(oldPassword, "oldPassword");
        String normalizedNewPassword = requireText(newPassword, "newPassword");

        User user = userDAO.findByUsername(normalizedUsername)
                .orElseThrow(() -> new AuthenticationException("Không tìm thấy username"));

        boolean oldPasswordMatches = false;
        try {
            oldPasswordMatches = PasswordUtil.verify(normalizedOldPassword, user.getPassword());
        } catch (Exception e) {
            oldPasswordMatches = user.getPassword().equals(normalizedOldPassword);
        }

        if (!oldPasswordMatches) {
            throw new AuthenticationException("Mật khẩu cũ không đúng");
        }

        user.setPassword(PasswordUtil.hash(normalizedNewPassword));
        userDAO.update(user);
        return user;
    }

    public List<User> getAllUsers() {
        return userDAO.findAll();
    }

    public Optional<User> findById(int id) {
        return userDAO.findById(requirePositiveId(id, "id"));
    }

    public Optional<User> findByUsername(String username) {
        return userDAO.findByUsername(requireText(username, "username"));
    }

    public Optional<User> findByEmail(String email) {
        return userDAO.findByEmail(requireText(email, "email"));
    }

    public User getCurrentUser() {
        return sessionManager.getCurrentUser();
    }

    public boolean isLoggedIn() {
        return sessionManager.isUserLoggedIn();
    }

    private User getRequiredUserById(int id) {
        return userDAO.findById(requirePositiveId(id, "id"))
                .orElseThrow(() -> new AuthenticationException("Không tìm thấy user"));
    }

    private void ensureUserCreatorsLoaded() {
        for (String creatorClass : USER_CREATOR_CLASSES) {
            try {
                Class.forName(creatorClass);
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("Không load được " + creatorClass, e);
            }
        }
    }

    private String normalizeRole(String role) {
        String normalizedRole = requireText(role, "role").toLowerCase();
        if (!normalizedRole.equals("seller")
                && !normalizedRole.equals("bidder")
                && !normalizedRole.equals("admin")) {
            throw new AuthenticationException("Role không hợp lệ");
        }
        return normalizedRole;
    }

    private String requireText(String value, String fieldName) {
        if (value == null) {
            throw new AuthenticationException(fieldName + " không được để trống");
        }

        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new AuthenticationException(fieldName + " không được để trống");
        }

        return trimmed;
    }

    private int generateUserId() {
        int id;
        do {
            id = ThreadLocalRandom.current().nextInt(100000, 999999);
        } while (userDAO.findById(id).isPresent());
        return id;
    }

    private int requirePositiveId(int value, String fieldName) {
        if (value <= 0) {
            throw new AuthenticationException(fieldName + " phải lớn hơn 0");
        }
        return value;
    }
}
