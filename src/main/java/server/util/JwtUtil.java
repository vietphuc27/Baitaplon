package server.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JwtUtil — Tạo và xác thực JWT token cho hệ thống đấu giá.
 * 
 * Token chứa:
 * - subject: userId (int)
 * - claim "username": tên đăng nhập
 * - claim "role": quyền (BIDDER, SELLER, ADMIN)
 * - issuedAt, expiration
 */
public final class JwtUtil {

    private static final String SECRET = "BaiTapLonNhom3HeThongDauGiaTrucTuyen2026!Kh0aBiMatJWT";
    private static final SecretKey SECRET_KEY = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    private static final long ACCESS_TOKEN_EXPIRATION_MS = 15 * 60 * 1000; // 15 phút (access token ngắn hạn)
    private static final long REFRESH_TOKEN_EXPIRATION_MS = 7 * 24 * 60 * 60 * 1000; // 7 ngày (refresh token dài hạn)

    private JwtUtil() {}

    /**
     * Tạo access token (ngắn hạn: 15 phút) cho user.
     */
    public static String generateToken(int userId, String username, String role) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + ACCESS_TOKEN_EXPIRATION_MS);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("role", role)
                .claim("token_type", "access")
                .issuedAt(now)
                .expiration(expiration)
                .signWith(SECRET_KEY)
                .compact();
    }

    /**
     * Tạo refresh token (dài hạn: 7 ngày) cho user.
     */
    public static String generateRefreshToken(int userId, String username, String role) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + REFRESH_TOKEN_EXPIRATION_MS);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("role", role)
                .claim("token_type", "refresh")
                .issuedAt(now)
                .expiration(expiration)
                .signWith(SECRET_KEY)
                .compact();
    }

    /**
     * Xác thực token và trả về Claims.
     * @return null nếu token không hợp lệ
     */
    public static Claims validateToken(String token) {
        if (token == null || token.isBlank()) return null;
        try {
            Jws<Claims> jws = Jwts.parser()
                    .verifyWith(SECRET_KEY)
                    .build()
                    .parseSignedClaims(token);
            return jws.getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Xác thực refresh token — kiểm tra token_type = "refresh".
     * @return null nếu không hợp lệ
     */
    public static Claims validateRefreshToken(String token) {
        Claims claims = validateToken(token);
        if (claims == null) return null;
        String tokenType = claims.get("token_type", String.class);
        if (!"refresh".equals(tokenType)) return null;
        return claims;
    }

    /**
     * Làm mới access token từ refresh token.
     * @return access token mới, hoặc null nếu refresh token không hợp lệ
     */
    public static String refreshAccessToken(String refreshToken) {
        Claims claims = validateRefreshToken(refreshToken);
        if (claims == null) return null;
        // Refresh token còn hạn → tạo access token mới
        int userId = Integer.parseInt(claims.getSubject());
        String username = claims.get("username", String.class);
        String role = claims.get("role", String.class);
        return generateToken(userId, username, role);
    }

    /**
     * Lấy userId từ token. Trả về -1 nếu không hợp lệ.
     */
    public static int getUserIdFromToken(String token) {
        Claims claims = validateToken(token);
        if (claims == null) return -1;
        try {
            return Integer.parseInt(claims.getSubject());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Lấy username từ token.
     */
    public static String getUsernameFromToken(String token) {
        Claims claims = validateToken(token);
        return claims != null ? claims.get("username", String.class) : null;
    }

    /**
     * Lấy role từ token.
     */
    public static String getRoleFromToken(String token) {
        Claims claims = validateToken(token);
        return claims != null ? claims.get("role", String.class) : null;
    }

    /**
     * Kiểm tra token còn hạn không.
     */
    public static boolean isTokenExpired(String token) {
        Claims claims = validateToken(token);
        if (claims == null) return true;
        return claims.getExpiration().before(new Date());
    }
}