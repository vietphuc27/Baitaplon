package server.util;

public class GenerateHashedPasswords {
    public static void main(String[] args) {
        String[] passwords = { "admin123", "seller123", "bidder123" };
        for (String pwd : passwords) {
            String hash = PasswordUtil.hash(pwd);
            System.out.println(pwd + " -> " + hash);
        }
    }
}