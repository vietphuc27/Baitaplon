package server.service;

import common.exceptions.AuthenticationException;
import common.models.user.User;
import common.models.user.UserStatus;
import server.manager.SessionManager;
import server.repository.UserDAO;
import server.util.JwtUtil;
import server.util.PasswordUtil;

import java.util.LinkedHashMap;
import java.util.Map;

public class AuthService {

    private final UserDAO userDAO;

    public AuthService() {
        this(new UserDAO());
    }

    public AuthService(UserDAO userDAO) {
        if (userDAO == null) {
            throw new IllegalArgumentException("Chưa có UserDao để xử lý đăng nhập");
        }
        this.userDAO = userDAO;
    }

    /**
     * Đăng nhập → trả về Map chứa: accessToken + refreshToken + user info.
     * Access token: hạn 15 phút
     * Refresh token: hạn 7 ngày
     */
    public Map<String, Object> login(String username, String password) {
        String normalizedUsername = requireText(username, "username");
        String normalizedPassword = requireText(password, "password");

        User user = userDAO.findByUsername(normalizedUsername)
                .orElseThrow(() -> new AuthenticationException("Không tìm thấy tên đăng nhập"));

        if (user.getStatus() == UserStatus.BANNED) {
            throw new AuthenticationException("Tài khoản đã bị khoá");
        }

        boolean passwordMatches = PasswordUtil.verify(normalizedPassword, user.getPassword());

        if (!passwordMatches) {
            throw new AuthenticationException("Sai mật khẩu");
        }

        user.setStatus(UserStatus.LOGIN);
        userDAO.update(user);
        SessionManager.getInstance().login(user);

        // Tạo access token (15 phút) + refresh token (7 ngày)
        String accessToken = JwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
        String refreshToken = JwtUtil.generateRefreshToken(user.getId(), user.getUsername(), user.getRole());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("accessToken", accessToken);
        result.put("refreshToken", refreshToken);
        result.put("user", user);
        return result;
    }

    /**
     * Làm mới access token từ refresh token.
     * @return Map chứa accessToken mới, hoặc throw exception nếu refresh token không hợp lệ
     */
    public Map<String, Object> refreshAccessToken(String refreshToken) {
        String refreshed = JwtUtil.refreshAccessToken(refreshToken);
        if (refreshed == null) {
            throw new AuthenticationException("Refresh token không hợp lệ hoặc đã hết hạn, vui lòng đăng nhập lại");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("accessToken", refreshed);
        return result;
    }

    /**
     * Xác thực token JWT → trả về User.
     * Kiểm tra token hết hạn, user bị BANNED.
     */
    public User authenticate(String token) {
        int userId = JwtUtil.getUserIdFromToken(token);
        if (userId <= 0) {
            throw new AuthenticationException("Token không hợp lệ hoặc đã hết hạn");
        }

        User user = userDAO.findById(userId)
                .orElseThrow(() -> new AuthenticationException("Không tìm thấy user của token"));

        if (user.getStatus() == UserStatus.BANNED) {
            throw new AuthenticationException("Tài khoản đã bị khoá");
        }

        return user;
    }

    public User getCurrentUser(String token) {
        return authenticate(token);
    }

    public void logout(String token) {
        // JWT stateless — chỉ cần clear session phía server
        int userId = JwtUtil.getUserIdFromToken(token);
        if (userId > 0) {
            userDAO.findById(userId).ifPresent(user -> {
                user.setStatus(UserStatus.LOGOUT);
                userDAO.update(user);
            });
        }
    }

    public void logoutAll(int userId) {
        requirePositiveId(userId, "userId");
        userDAO.findById(userId).ifPresent(user -> {
            user.setStatus(UserStatus.LOGOUT);
            userDAO.update(user);
        });
    }

    /**
     * Kiểm tra token JWT có hợp lệ không (dùng cho middleware).
     */
    public boolean validateToken(String token) {
        return JwtUtil.validateToken(token) != null;
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

    private int requirePositiveId(int value, String fieldName) {
        if (value <= 0) {
            throw new AuthenticationException(fieldName + " phải lớn hơn 0");
        }
        return value;
    }
}