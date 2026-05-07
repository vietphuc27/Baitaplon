package server.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

public final class DatabaseConnection {
    private static final String DEFAULT_URL = "jdbc:mysql://localhost:3306/auction_db";
    private static final String DEFAULT_USER = "root";
    private static final String DEFAULT_PASSWORD = "";
    private static volatile boolean schemaInitialized = false;

    private DatabaseConnection() {
    }

    public static Connection getConnection() throws SQLException {
        String rawUrl = getConfig("db.url", "DB_URL", DEFAULT_URL);
        String user = getConfig("db.user", "DB_USER", DEFAULT_USER);
        String password = getConfig("db.password", "DB_PASSWORD", DEFAULT_PASSWORD);

        String url = ensureCreateDatabaseIfMissing(rawUrl);
        Connection connection = DriverManager.getConnection(url, user, password);
        initializeSchemaIfNeeded(connection);
        return connection;
    }

    private static String ensureCreateDatabaseIfMissing(String url) {
        if (url.contains("createDatabaseIfNotExist=")) {
            return url;
        }
        if (url.contains("?")) {
            return url + "&createDatabaseIfNotExist=true";
        }
        return url + "?createDatabaseIfNotExist=true";
    }

    private static void initializeSchemaIfNeeded(Connection connection) throws SQLException {
        if (schemaInitialized) {
            return;
        }
        synchronized (DatabaseConnection.class) {
            if (schemaInitialized) {
                return;
            }

            try (Statement stmt = connection.createStatement()) {
                stmt.execute("""
                        CREATE TABLE IF NOT EXISTS users (
                            id INT PRIMARY KEY,
                            username VARCHAR(100) NOT NULL UNIQUE,
                            email VARCHAR(255) NOT NULL UNIQUE,
                            password VARCHAR(255) NOT NULL,
                            role VARCHAR(20) NOT NULL,
                            status VARCHAR(20) NOT NULL
                        )
                        """);

                stmt.execute("""
                        CREATE TABLE IF NOT EXISTS items (
                            id INT PRIMARY KEY,
                            name VARCHAR(255) NOT NULL,
                            description TEXT NOT NULL,
                            starting_price DOUBLE NOT NULL,
                            seller_id VARCHAR(255) NOT NULL,
                            item_type VARCHAR(30) NOT NULL,
                            warranty_period INT NULL,
                            mileage INT NULL,
                            artist VARCHAR(255) NULL
                        )
                        """);

                stmt.execute("""
                        CREATE TABLE IF NOT EXISTS auctions (
                            id INT PRIMARY KEY,
                            item_id INT NOT NULL,
                            seller_id VARCHAR(255) NOT NULL,
                            start_time DATETIME NOT NULL,
                            end_time DATETIME NOT NULL,
                            current_highest_bid DOUBLE NOT NULL DEFAULT 0,
                            current_leader_id INT NULL,
                            status VARCHAR(20) NOT NULL,
                            INDEX idx_auctions_item_id (item_id),
                            INDEX idx_auctions_seller_id (seller_id)
                        )
                        """);

                stmt.execute("""
                        CREATE TABLE IF NOT EXISTS bid_transactions (
                            id INT PRIMARY KEY,
                            auction_id INT NOT NULL,
                            bidder_id INT NOT NULL,
                            bid_amount DOUBLE NOT NULL,
                            bid_time DATETIME NOT NULL,
                            INDEX idx_bid_transactions_auction_id (auction_id),
                            INDEX idx_bid_transactions_bidder_id (bidder_id)
                        )
                        """);
            }

            ensureSeedAdmin(connection);
            schemaInitialized = true;
        }
    }

    private static void ensureSeedAdmin(Connection connection) throws SQLException {
        try (PreparedStatement checkStmt = connection.prepareStatement("SELECT id FROM users WHERE username = ?")) {
            checkStmt.setString(1, "admin");
            if (checkStmt.executeQuery().next()) {
                return;
            }
        }

        try (PreparedStatement insertStmt = connection.prepareStatement(
                "INSERT INTO users (id, username, password, role, email, status) VALUES (?, ?, ?, ?, ?, ?)")) {
            insertStmt.setInt(1, 100001); // Use int instead of String
            insertStmt.setString(2, "admin");
            insertStmt.setString(3, "admin123");
            insertStmt.setString(4, "ADMIN");
            insertStmt.setString(5, "admin@auction.local");
            insertStmt.setString(6, "LOGOUT");
            insertStmt.executeUpdate();
        }
    }

    private static String getConfig(String propertyName, String envName, String defaultValue) {
        String value = System.getProperty(propertyName);
        if (value == null || value.isBlank()) {
            value = System.getenv(envName);
        }
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
