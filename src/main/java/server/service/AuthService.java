package server.service;

import common.exceptions.AuthenticationException;
import common.models.user.User;
import common.models.user.UserStatus;
import server.manager.SessionManager;
import server.repository.dao.UserDAO;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class AuthService {
    private static final int TOKEN_BYTE_LENGTH = 32;
    private static final long DEFAULT_TOKEN_TTL_MINUTES = 120;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Map<String, AuthSession> TOKEN_STORE = new ConcurrentHashMap<>();

    private final UserDAO userDAO;
    private final SessionManager sessionManager;
    private final long tokenTtlMinutes;

    public AuthService() {
        this(new UserDAO(), DEFAULT_TOKEN_TTL_MINUTES);
    }

    public AuthService(UserDAO userDAO) {
        this(userDAO, DEFAULT_TOKEN_TTL_MINUTES);
    }

    public AuthService(UserDAO userDAO, long tokenTtlMinutes) {
        if (userDAO == null) {
            throw new IllegalArgumentException("Chưa có UserDao để xử lý đăng nhập");
        }
        if (tokenTtlMinutes <= 0) {
            throw new IllegalArgumentException("Thời gian tồn tại của token phải lớn hơn 0");
        }

        this.userDAO = userDAO;
        this.sessionManager = SessionManager.getInstance();
        this.tokenTtlMinutes = tokenTtlMinutes;
    }

    public String login(String username, String password) {
        String normalizedUsername = requireText(username, "username");
        String normalizedPassword = requireText(password, "password");

        User user = userDAO.findByUsername(normalizedUsername)
                .orElseThrow(() -> new AuthenticationException("Không tìm thấy tên đăng nhập"));

        if (user.getStatus() == UserStatus.BANNED) {
            throw new AuthenticationException("Tài khoản đã bị khoá");
        }

        if (!user.getPassword().equals(normalizedPassword)) {
            throw new AuthenticationException("Sai mật khẩu");
        }

        user.setStatus(UserStatus.LOGIN);
        userDAO.update(user);
        sessionManager.login(user);

        String token = generateToken();
        TOKEN_STORE.put(token, new AuthSession(user.getId(), LocalDateTime.now().plusMinutes(tokenTtlMinutes)));
        return token;
    }

    public boolean validateToken(String token) {
        return findValidSession(token).isPresent();
    }

    public User authenticate(String token) {
        AuthSession session = findValidSession(token)
                .orElseThrow(() -> new AuthenticationException("Token không hợp lệ hoặc đã hết hạn"));

        User user = userDAO.findById(session.userId())
                .orElseThrow(() -> new AuthenticationException("Không tìm thấy user của token"));

        if (user.getStatus() == UserStatus.BANNED) {
            TOKEN_STORE.remove(normalizeToken(token));
            throw new AuthenticationException("Tài khoản đã bị khoá");
        }

        return user;
    }

    public User getCurrentUser(String token) {
        return authenticate(token);
    }

    public void logout(String token) {
        String normalizedToken = normalizeToken(token);
        AuthSession session = findValidSession(normalizedToken).orElse(null);
        if (session == null) {
            TOKEN_STORE.remove(normalizedToken);
            return;
        }

        userDAO.findById(session.userId()).ifPresent(user -> {
            user.setStatus(UserStatus.LOGOUT);
            userDAO.update(user);

            User currentUser = sessionManager.getCurrentUser();
            if (currentUser != null && currentUser.getId().equals(user.getId())) {
                sessionManager.logout();
            }
        });

        TOKEN_STORE.remove(normalizedToken);
    }

    public void logoutAll(String userId) {
        String normalizedUserId = requireText(userId, "userId");
        TOKEN_STORE.entrySet().removeIf(entry -> entry.getValue().userId().equals(normalizedUserId));

        userDAO.findById(normalizedUserId).ifPresent(user -> {
            user.setStatus(UserStatus.LOGOUT);
            userDAO.update(user);
        });

        User currentUser = sessionManager.getCurrentUser();
        if (currentUser != null && currentUser.getId().equals(normalizedUserId)) {
            sessionManager.logout();
        }
    }

    public int clearExpiredTokens() {
        int before = TOKEN_STORE.size();
        TOKEN_STORE.entrySet().removeIf(entry -> entry.getValue().isExpired());
        return before - TOKEN_STORE.size();
    }

    private Optional<AuthSession> findValidSession(String token) {
        String normalizedToken = normalizeToken(token);
        if (normalizedToken.isEmpty()) {
            return Optional.empty();
        }

        AuthSession session = TOKEN_STORE.get(normalizedToken);
        if (session == null) {
            return Optional.empty();
        }

        if (session.isExpired()) {
            TOKEN_STORE.remove(normalizedToken);
            return Optional.empty();
        }

        return Optional.of(session);
    }

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTE_LENGTH];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String normalizeToken(String token) {
        return token == null ? "" : token.trim();
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

    private record AuthSession(String userId, LocalDateTime expiresAt) {
        private boolean isExpired() {
            return LocalDateTime.now().isAfter(expiresAt);
        }
    }
}
