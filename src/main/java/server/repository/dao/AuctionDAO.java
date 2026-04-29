package server.repository.dao;

import common.models.auction.*;
import common.models.item.*;
import server.config.DatabaseConnection;
import server.repository.AuctionRepository;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class AuctionDAO implements AuctionRepository {

    // ─── TÁCH CÂU SQL GỐC CHO GỌN CHỖ JOIN VỚI BẢNG ITEMS ───────────
    private static final String SELECT_BASE_QUERY =
            "SELECT a.*, "
                    + "i.name as item_name, i.description, i.starting_price, "
                    + "i.seller_id as item_seller_id, i.item_type, i.warranty_period, i.mileage, i.artist "
                    + "FROM auctions a JOIN items i ON a.item_id = i.id ";

    // ─── SAVE ─────────────────────────────────────────────────────
    @Override
    public void save(Auction auction) {
        String sql = "INSERT INTO auctions "
                + "(id, item_id, seller_id, start_time, end_time, "
                + "current_highest_bid, current_leader_id, status) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, auction.getAuctionId());
            stmt.setString(2, auction.getItem().getId());
            stmt.setString(3, auction.getSellerId());
            //  Dùng Timestamp để lưu LocalDateTime xuống SQL
            stmt.setTimestamp(4, Timestamp.valueOf(auction.getStartTime()));
            stmt.setTimestamp(5, Timestamp.valueOf(auction.getEndTime()));
            stmt.setDouble(6, auction.getCurrentHighestBid());
            stmt.setString(7, auction.getCurrentLeaderId());
            stmt.setString(8, auction.getStatus().name());

            stmt.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Lỗi lưu auction: " + e.getMessage());
        }
    }

    // ─── FIND BY ID ───────────────────────────────────────────────
    @Override
    public Optional<Auction> findById(String id) {
        String sql = SELECT_BASE_QUERY + "WHERE a.id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, id);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) return Optional.of(mapToAuction(rs));
            return Optional.empty();

        } catch (SQLException e) {
            throw new RuntimeException("Lỗi tìm auction: " + e.getMessage());
        }
    }

    // ─── FIND ALL ─────────────────────────────────────────────────
    @Override
    public List<Auction> findAll() {
        List<Auction> list = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SELECT_BASE_QUERY);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) list.add(mapToAuction(rs));
            return list;
        } catch (SQLException e) {
            throw new RuntimeException("Lỗi lấy danh sách auction: " + e.getMessage());
        }
    }

    // ─── FIND BY STATUS ───────────────────────────────────────────
    @Override
    public List<Auction> findByStatus(AuctionStatus status) {
        String sql = SELECT_BASE_QUERY + "WHERE a.status = ?";
        List<Auction> list = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, status.name());
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) list.add(mapToAuction(rs));
            return list;

        } catch (SQLException e) {
            throw new RuntimeException("Lỗi tìm auction theo status: " + e.getMessage());
        }
    }

    // ─── FIND BY SELLER ───────────────────────────────────────────
    @Override
    public List<Auction> findBySellerId(String sellerId) {
        String sql = SELECT_BASE_QUERY + "WHERE a.seller_id = ?";
        List<Auction> list = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, sellerId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) list.add(mapToAuction(rs));
            return list;

        } catch (SQLException e) {
            throw new RuntimeException("Lỗi tìm auction theo seller: " + e.getMessage());
        }
    }

    // ─── FIND EXPIRED — Dùng cho auto-close scheduler ─────────────
    @Override
    public List<Auction> findExpiredButNotClosed() {
        String sql = SELECT_BASE_QUERY + "WHERE a.status = 'RUNNING' AND a.end_time <= ?";
        List<Auction> list = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            //So sánh LocalDateTime thay vì dùng long System.currentTimeMillis()
            stmt.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()));
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) list.add(mapToAuction(rs));
            return list;

        } catch (SQLException e) {
            throw new RuntimeException("Lỗi tìm auction hết hạn: " + e.getMessage());
        }
    }

    // ─── UPDATE ───────────────────────────────────────────────────
    @Override
    public void update(Auction auction) {
        String sql = "UPDATE auctions "
                + "SET current_highest_bid = ?, current_leader_id = ?, "
                + "status = ?, end_time = ? "
                + "WHERE id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setDouble(1, auction.getCurrentHighestBid());
            stmt.setString(2, auction.getCurrentLeaderId());
            stmt.setString(3, auction.getStatus().name());
            stmt.setTimestamp(4, Timestamp.valueOf(auction.getEndTime())); // Fix Timestamp
            stmt.setString(5, auction.getAuctionId());
            stmt.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Lỗi cập nhật auction: " + e.getMessage());
        }
    }

    // ─── DELETE ───────────────────────────────────────────────────
    @Override
    public void delete(String id) {
        String sql = "DELETE FROM auctions WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, id);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Lỗi xóa auction: " + e.getMessage());
        }
    }

    // ─── HELPER: ResultSet → Auction ──────────────────────────────
    private Auction mapToAuction(ResultSet rs) throws SQLException {
        String id              = rs.getString("id");
        String sellerId        = rs.getString("seller_id");

        // Đã fix: Dùng getTimestamp().toLocalDateTime() để lấy đúng format
        LocalDateTime startTime = rs.getTimestamp("start_time").toLocalDateTime();
        LocalDateTime endTime   = rs.getTimestamp("end_time").toLocalDateTime();

        double currentBid      = rs.getDouble("current_highest_bid");
        String currentLeaderId = rs.getString("current_leader_id");
        String status          = rs.getString("status");

        // Map item từ JOIN
        Item item = mapToItem(rs);

        Auction auction = new Auction(id, item, sellerId, startTime, endTime);
        auction.setCurrentHighestBid(currentBid);
        auction.setCurrentLeaderId(currentLeaderId);
        auction.setStatus(AuctionStatus.valueOf(status));
        return auction;
    }

    // Map phần item trong JOIN
    private Item mapToItem(ResultSet rs) throws SQLException {
        String itemId        = rs.getString("item_id");
        String name          = rs.getString("item_name");
        String description   = rs.getString("description");
        double startingPrice = rs.getDouble("starting_price");
        String sellerId      = rs.getString("item_seller_id"); // Lấy từ alias đã tạo
        String type          = rs.getString("item_type");

        return switch (type) {
            case "ELECTRONICS" -> new Electronics(
                    itemId, name, description, startingPrice,
                    sellerId, rs.getInt("warranty_period")
            );
            case "VEHICLE" -> new Vehicle(
                    itemId, name, description, startingPrice,
                    sellerId, rs.getInt("mileage")
            );
            case "ART" -> new Art(
                    // Lấy thêm trường artist từ ResultSet cho tranh (Đã sửa câu SQL bên trên)
                    itemId, name, description, startingPrice, sellerId, rs.getString("artist")
            );
            default -> throw new SQLException("Item type không hợp lệ: " + type);
        };
    }
}