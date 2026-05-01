package server.service;

import common.exceptions.AuthenticationException;
import common.models.user.User;
import common.models.user.UserStatus;
import common.userfactory.UserFactory;
import server.manager.SessionManager;
import server.repository.dao.UserDAO;

import java.util.List;
import java.util.Optional;

public class UserService {
    private static final String[] USER_CREATOR_CLASSES = {
            "common.userfactory.SellerCreator",
            "common.userfactory.BidderCreator",
            "common.userfactory.AdminCreator"
    };

    private final UserDAO userDAO;
    private final SessionManager sessionManager;

    public UserService() {
        this(new UserDAO());
    }

    public UserService(UserDAO userDAO) {
        this.userDAO = userDAO;
        this.sessionManager = SessionManager.getInstance();
        ensureUserCreatorsLoaded();
    }

    public User register(String id, String username, String email, String password, String role) {
        String normalizedId = requireText(id, "id");
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

        User user = UserFactory.createUser(
                normalizedRole,
                normalizedId,
                normalizedUsername,
                normalizedEmail,
                normalizedPassword
        );

        user.setStatus(UserStatus.LOGOUT);
        userDAO.save(user);
        return user;
    }

    public User login(String username, String password) {
        String normalizedUsername = requireText(username, "username");
        String normalizedPassword = requireText(password, "password");

        User user = userDAO.findByUsername(normalizedUsername)
                .orElseThrow(() -> new AuthenticationException("Không tìm thấy username"));

        if (user.getStatus() == UserStatus.BANNED) {
            throw new AuthenticationException("Tài khoản đã bị khóa");
        }

        if (!user.getPassword().equals(normalizedPassword)) {
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

    public User banUser(String id) {
        User user = getRequiredUserById(id);
        user.setStatus(UserStatus.BANNED);
        userDAO.update(user);

        User currentUser = sessionManager.getCurrentUser();
        if (currentUser != null && currentUser.getId().equals(user.getId())) {
            sessionManager.logout();
        }

        return user;
    }

    public User unbanUser(String id) {
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

        if (!user.getPassword().equals(normalizedOldPassword)) {
            throw new AuthenticationException("Mật khẩu cũ không đúng");
        }

        user.setPassword(normalizedNewPassword);
        userDAO.update(user);
        return user;
    }

    public List<User> getAllUsers() {
        return userDAO.findAll();
    }

    public Optional<User> findById(String id) {
        return userDAO.findById(requireText(id, "id"));
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

    private User getRequiredUserById(String id) {
        return userDAO.findById(requireText(id, "id"))
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
}
