package server.util;

import server.config.DatabaseConnection;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;

public class MigratePasswords {
    public static void main(String[] args) {
        Map<String, String> hashedPasswords = new HashMap<>();
        hashedPasswords.put("seller1", "+TR7XbpCnsTSxMSZXslV5g==:2qja5EQZOUswf7r5we6op0kENVgTbPNsotKjjBWONv8=");
        hashedPasswords.put("bidder1", "/NyfAyFWEws1zrj435Oiew==:suqrJwlAWbOrdTxgwP0cRmySqDpdVftdYkmhToM60fE=");
        hashedPasswords.put("bidder2", "/NyfAyFWEws1zrj435Oiew==:suqrJwlAWbOrdTxgwP0cRmySqDpdVftdYkmhToM60fE=");

        try (Connection conn = DatabaseConnection.getConnection()) {
            String query = "SELECT id, username, password FROM users WHERE username = ?";
            String update = "UPDATE users SET password = ? WHERE username = ?";

            for (Map.Entry<String, String> entry : hashedPasswords.entrySet()) {
                String username = entry.getKey();
                String hashedPwd = entry.getValue();

                // Check current password
                try (PreparedStatement ps = conn.prepareStatement(query)) {
                    ps.setString(1, username);
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) {
                        String currentPwd = rs.getString("password");
                        System.out.println("User: " + username + " | Current password: " + currentPwd);

                        // Update to hashed
                        try (PreparedStatement psUpdate = conn.prepareStatement(update)) {
                            psUpdate.setString(1, hashedPwd);
                            psUpdate.setString(2, username);
                            int rows = psUpdate.executeUpdate();
                            System.out.println("  -> Updated: " + rows + " row(s)");
                        }
                    } else {
                        System.out.println("User: " + username + " not found!");
                    }
                }
            }
            System.out.println("\nMigration completed. Passwords updated for seller1, bidder1, bidder2.");
            System.out.println("Admin (admin123) kept as plaintext - will still work via old code path.");
        } catch (Exception e) {
            System.err.println("Migration failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}