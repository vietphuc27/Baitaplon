package server.util;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

/**
 * Tiện ích mã hóa mật khẩu dùng PBKDF2WithHmacSHA256.
 * Không cần thư viện ngoài, dùng Java Security có sẵn trong JDK.
 */
public class PasswordUtil {

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 310_000;
    private static final int KEY_LENGTH = 256;
    private static final int SALT_LENGTH = 16;
    private static final String SEPARATOR = ":";

    /**
     * Băm mật khẩu. Kết quả có dạng "salt:hash" (Base64).
     */
    public static String hash(String plainPassword) {
        byte[] salt = generateSalt();
        byte[] hash = pbkdf2(plainPassword.toCharArray(), salt);
        return Base64.getEncoder().encodeToString(salt)
                + SEPARATOR
                + Base64.getEncoder().encodeToString(hash);
    }

    /**
     * Kiểm tra mật khẩu plain-text có khớp với hash đã lưu không.
     */
    public static boolean verify(String plainPassword, String storedHash) {
        // Hỗ trợ tương thích ngược: nếu hash chưa được băm (không chứa ":") thì so sánh trực tiếp
        if (!storedHash.contains(SEPARATOR)) {
            return storedHash.equals(plainPassword);
        }

        String[] parts = storedHash.split(SEPARATOR, 2);
        if (parts.length != 2) return false;

        byte[] salt = Base64.getDecoder().decode(parts[0]);
        byte[] expectedHash = Base64.getDecoder().decode(parts[1]);
        byte[] actualHash = pbkdf2(plainPassword.toCharArray(), salt);

        return slowEquals(expectedHash, actualHash);
    }

    // ── Private helpers ──────────────────────────────────────────────

    private static byte[] generateSalt() {
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[SALT_LENGTH];
        random.nextBytes(salt);
        return salt;
    }

    private static byte[] pbkdf2(char[] password, byte[] salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, KEY_LENGTH);
            SecretKeyFactory skf = SecretKeyFactory.getInstance(ALGORITHM);
            return skf.generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new RuntimeException("Lỗi khi mã hóa mật khẩu", e);
        }
    }

    /** So sánh constant-time để chống timing attack */
    private static boolean slowEquals(byte[] a, byte[] b) {
        int diff = a.length ^ b.length;
        for (int i = 0; i < a.length && i < b.length; i++) {
            diff |= a[i] ^ b[i];
        }
        return diff == 0;
    }
}
