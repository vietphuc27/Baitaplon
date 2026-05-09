package server.repository;

import common.models.user.*;
import server.config.DatabaseConnection;
import server.repository.dao.UserRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class UserDAO implements UserRepository {

    // ─── SAVE ─────────────────────────────────────────────────────
    @Override
    public void save(User user) {
        String sql = "INSERT INTO users (id, username, email, password, role, status) "
                + "VALUES (?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, user.getId());
            stmt.setString(2, user.getUsername());
            stmt.setString(3, user.getEmail());
            stmt.setString(4, user.getPassword());
            stmt.setString(5, user.getRole());
            stmt.setString(6, user.getStatus().name());
            stmt.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Lỗi lưu user: " + e.getMessage());
        }
    }

    // ─── FIND BY ID ───────────────────────────────────────────────
    @Override
    public Optional<User> findById(int id) {
        String sql = "SELECT * FROM users WHERE id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, id);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) return Optional.of(mapToUser(rs));
            return Optional.empty();

        } catch (SQLException e) {
            throw new RuntimeException("Lỗi tìm user: " + e.getMessage());
        }
    }

    // ─── FIND ALL ─────────────────────────────────────────────────
    @Override
    public List<User> findAll() {
        String sql = "SELECT * FROM users";
        List<User> list = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) list.add(mapToUser(rs));
            return list;

        } catch (SQLException e) {
            throw new RuntimeException("Lỗi lấy danh sách user: " + e.getMessage());
        }
    }

    // ─── UPDATE ───────────────────────────────────────────────────
    @Override
    public void update(User user) {
        String sql = "UPDATE users SET username=?, email=?, password=?, status=? "
                + "WHERE id=?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, user.getUsername());
            stmt.setString(2, user.getEmail());
            stmt.setString(3, user.getPassword());
            stmt.setString(4, user.getStatus().name());
            stmt.setInt(5, user.getId());
            stmt.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Lỗi cập nhật user: " + e.getMessage());
        }
    }

    // ─── DELETE ───────────────────────────────────────────────────
    @Override
    public void delete(int id) {
        String sql = "DELETE FROM users WHERE id=?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, id);
            stmt.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Lỗi xóa user: " + e.getMessage());
        }
    }

    // ─── FIND BY USERNAME ─────────────────────────────────────────
    @Override
    public Optional<User> findByUsername(String username) {
        String sql = "SELECT * FROM users WHERE username=?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) return Optional.of(mapToUser(rs));
            return Optional.empty();

        } catch (SQLException e) {
            throw new RuntimeException("Lỗi tìm username: " + e.getMessage());
        }
    }

    // ─── FIND BY EMAIL ────────────────────────────────────────────
    @Override
    public Optional<User> findByEmail(String email) {
        String sql = "SELECT * FROM users WHERE email=?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, email);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) return Optional.of(mapToUser(rs));
            return Optional.empty();

        } catch (SQLException e) {
            throw new RuntimeException("Lỗi tìm email: " + e.getMessage());
        }
    }

    // ─── EXISTS BY USERNAME ───────────────────────────────────────
    @Override
    public boolean existsByUsername(String username) {
        String sql = "SELECT 1 FROM users WHERE username=?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, username);
            return stmt.executeQuery().next();

        } catch (SQLException e) {
            throw new RuntimeException("Lỗi kiểm tra username: " + e.getMessage());
        }
    }

    // ─── EXISTS BY EMAIL ──────────────────────────────────────────
    @Override
    public boolean existsByEmail(String email) {
        String sql = "SELECT 1 FROM users WHERE email=?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, email);
            return stmt.executeQuery().next();

        } catch (SQLException e) {
            throw new RuntimeException("Lỗi kiểm tra email: " + e.getMessage());
        }
    }

    // ─── HELPER: ResultSet → User ─────────────────────────────────
    private User mapToUser(ResultSet rs) throws SQLException {
        int id          = rs.getInt("id");
        String username = rs.getString("username");
        String email    = rs.getString("email");
        String password = rs.getString("password");
        String role     = rs.getString("role");
        String status   = rs.getString("status");

        User user = switch (role) {
            case "BIDDER" -> new Bidder(id, username, email, password);
            case "SELLER" -> new Seller(id, username, email, password);
            case "ADMIN"  -> new Admin(id, username, email, password);
            default -> throw new SQLException("Role không hợp lệ: " + role);
        };

        user.setStatus(UserStatus.valueOf(status));
        return user;
    }
}
