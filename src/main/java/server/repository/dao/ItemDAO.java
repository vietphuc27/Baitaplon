package server.repository.dao;

import common.models.item.*;
import server.config.DatabaseConnection;
import server.repository.ItemRepository;

import java.sql.*;
import java.util.*;

public class ItemDAO implements ItemRepository {

    // ─── SAVE ─────────────────────────────────────────────────────
    @Override
    public void save(Item item) {
        String sql = "INSERT INTO items "
                + "(id, name, description, starting_price, seller_id, "
                + "item_type, warranty_period, mileage, artist) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql)) {

            stmt.setString(1, item.getId());
            stmt.setString(2, item.getName());
            stmt.setString(3, item.getDescription());
            stmt.setDouble(4, item.getStartingPrice());
            stmt.setString(5, item.getSellerId());
            stmt.setString(6, item.getClass().getSimpleName().toUpperCase());
            if (item instanceof Electronics e) {
                stmt.setInt(7, e.getWarrantyPeriod());
                stmt.setNull(8, Types.INTEGER);
                stmt.setNull(9, Types.VARCHAR);
            } else if (item instanceof Vehicle v) {
                stmt.setNull(7, Types.INTEGER);
                stmt.setInt(8, v.getMileage());
                stmt.setNull(9, Types.VARCHAR);
            } else if (item instanceof Art a) {
                stmt.setNull(7, Types.INTEGER);
                stmt.setNull(8, Types.INTEGER);
                stmt.setString(9, a.getArtist());
            } else {
                stmt.setNull(7, Types.INTEGER);
                stmt.setNull(8, Types.INTEGER);
                stmt.setNull(9, Types.VARCHAR);
            }
            stmt.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Lỗi lưu item: " + e.getMessage());
        }
    }

    // ─── FIND BY ID ───────────────────────────────────────────────
    @Override
    public Optional<Item> findById(String id) {
        String sql = "SELECT * FROM items WHERE id=?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, id);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) return Optional.of(mapToItem(rs));
            return Optional.empty();

        } catch (SQLException e) {
            throw new RuntimeException("Lỗi tìm item: " + e.getMessage());
        }
    }

    // ─── FIND ALL ─────────────────────────────────────────────────
    @Override
    public List<Item> findAll() {
        String sql = "SELECT * FROM items";
        List<Item> list = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) list.add(mapToItem(rs));
            return list;

        } catch (SQLException e) {
            throw new RuntimeException("Lỗi lấy danh sách item: " + e.getMessage());
        }
    }

    // ─── FIND BY SELLER ID ────────────────────────────────────────
    @Override
    public List<Item> findBySellerId(String sellerId) {
        String sql = "SELECT * FROM items WHERE seller_id=?";
        List<Item> list = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, sellerId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) list.add(mapToItem(rs));
            return list;

        } catch (SQLException e) {
            throw new RuntimeException("Lỗi tìm item theo seller: " + e.getMessage());
        }
    }

    // ─── FIND BY NAME ─────────────────────────────────────────────
    @Override
    public List<Item> findByNameContaining(String keyword) {
        String sql = "SELECT * FROM items WHERE name LIKE ?";
        List<Item> list = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, "%" + keyword + "%");
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) list.add(mapToItem(rs));
            return list;

        } catch (SQLException e) {
            throw new RuntimeException("Lỗi tìm kiếm item: " + e.getMessage());
        }
    }

    // ─── UPDATE ───────────────────────────────────────────────────
    @Override
    public void update(Item item) {
        String sql = "UPDATE items SET name=?, description=?, starting_price=?, "
                + "warranty_period=?, mileage=?, artist=? WHERE id=?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, item.getName());
            stmt.setString(2, item.getDescription());
            stmt.setDouble(3, item.getStartingPrice());

            if (item instanceof Electronics e) {
                stmt.setInt(4, e.getWarrantyPeriod());
                stmt.setNull(5, Types.INTEGER);
                stmt.setNull(6, Types.VARCHAR);
            } else if (item instanceof Vehicle v) {
                stmt.setNull(4, Types.INTEGER);
                stmt.setInt(5, v.getMileage());
                stmt.setNull(6, Types.VARCHAR);
            } else if (item instanceof Art a) {
                stmt.setNull(4, Types.INTEGER);
                stmt.setNull(5, Types.INTEGER);
                stmt.setString(6, a.getArtist());
            } else {
                stmt.setNull(4, Types.INTEGER);
                stmt.setNull(5, Types.INTEGER);
                stmt.setNull(6, Types.VARCHAR);
            }

            stmt.setString(7, item.getId());
            stmt.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Lỗi cập nhật item: " + e.getMessage());
        }
    }

    // ─── DELETE ───────────────────────────────────────────────────
    @Override
    public void delete(String id) {
        String sql = "DELETE FROM items WHERE id=?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, id);
            stmt.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Lỗi xóa item: " + e.getMessage());
        }
    }

    // ─── HELPER: ResultSet → Item ─────────────────────────────────
    private Item mapToItem(ResultSet rs) throws SQLException {
        String id            = rs.getString("id");
        String name          = rs.getString("name");
        String description   = rs.getString("description");
        double startingPrice = rs.getDouble("starting_price");
        String sellerId      = rs.getString("seller_id");
        String type          = rs.getString("item_type");

        return switch (type) {
            case "ELECTRONICS" -> new Electronics(
                    id, name, description, startingPrice,
                    sellerId, rs.getInt("warranty_period")
            );
            case "VEHICLE" -> new Vehicle(
                    id, name, description, startingPrice,
                    sellerId, rs.getInt("mileage")
            );
            case "ART" -> {
                String artist = rs.getString("artist");
                yield new Art(id, name, description, startingPrice, sellerId, artist);
            }
            default -> throw new SQLException("Item type không hợp lệ: " + type);
        };
    }
}