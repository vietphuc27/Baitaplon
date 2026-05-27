package server.config;

import java.sql.Connection;
import java.sql.Statement;

/**
 * Applies database schema migrations for new features.
 */
public class DatabaseMigration {

    public static void migrate() {
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            
            // Add image_url column to items table if it doesn't exist
            try {
                stmt.execute("ALTER TABLE items ADD COLUMN image_url VARCHAR(500) DEFAULT NULL");
                System.out.println("[Migration] Added image_url column to items table.");
            } catch (java.sql.SQLException e) {
                // Column may already exist, that's fine
                if (!e.getMessage().contains("Duplicate column")) {
                    System.err.println("[Migration] Warning: " + e.getMessage());
                }
            }
            
        } catch (Exception e) {
            System.err.println("[Migration] Error: " + e.getMessage());
        }
    }
}