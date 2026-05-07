package server.repository.dao;

import common.models.auction.BidTransaction;
import server.config.DatabaseConnection;
import server.repository.BidTransactionRepository;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class BidTransactionDAO implements BidTransactionRepository {

    // ─── SAVE ─────────────────────────────────────────────────────
    @Override
    public void save(BidTransaction bid) {
        String sql = "INSERT INTO bid_transactions (id, auction_id, bidder_id, bid_amount, bid_time) "
                + "VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, bid.getId());
            stmt.setInt(2, bid.getAuctionId());
            stmt.setInt(3, bid.getBidderId());
            stmt.setDouble(4, bid.getBidAmount());
            // Chuyển LocalDateTime sang java.sql.Timestamp
            stmt.setTimestamp(5, Timestamp.valueOf(bid.getBidTime()));

            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Lỗi lưu BidTransaction: " + e.getMessage());
        }
    }

    // ─── FIND BY ID ───────────────────────────────────────────────
    @Override
    public Optional<BidTransaction> findById(int id) {
        String sql = "SELECT * FROM bid_transactions WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, id);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) return Optional.of(mapToBidTransaction(rs));
            return Optional.empty();
        } catch (SQLException e) {
            throw new RuntimeException("Lỗi tìm BidTransaction theo ID: " + e.getMessage());
        }
    }

    // ─── FIND ALL ─────────────────────────────────────────────────
    @Override
    public List<BidTransaction> findAll() {
        String sql = "SELECT * FROM bid_transactions ORDER BY bid_time DESC";
        List<BidTransaction> list = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) list.add(mapToBidTransaction(rs));
            return list;
        } catch (SQLException e) {
            throw new RuntimeException("Lỗi lấy danh sách tất cả BidTransaction: " + e.getMessage());
        }
    }

    // ─── FIND BY AUCTION ID ───────────────────────────────────────
    @Override
    public List<BidTransaction> findByAuctionId(int auctionId) {
        // Lấy lịch sử bid của 1 phiên, xếp từ mới nhất / cao nhất đưa lên đầu
        String sql = "SELECT * FROM bid_transactions WHERE auction_id = ? ORDER BY bid_amount DESC, bid_time DESC";
        List<BidTransaction> list = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, auctionId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) list.add(mapToBidTransaction(rs));
            return list;
        } catch (SQLException e) {
            throw new RuntimeException("Lỗi lấy lịch sử bid của Auction: " + e.getMessage());
        }
    }

    // ─── FIND BY BIDDER ID ────────────────────────────────────────
    @Override
    public List<BidTransaction> findByBidderId(int bidderId) {
        String sql = "SELECT * FROM bid_transactions WHERE bidder_id = ? ORDER BY bid_time DESC";
        List<BidTransaction> list = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, bidderId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) list.add(mapToBidTransaction(rs));
            return list;
        } catch (SQLException e) {
            throw new RuntimeException("Lỗi lấy lịch sử bid của Bidder: " + e.getMessage());
        }
    }

    // ─── FIND HIGHEST BID OF AUCTION ──────────────────────────────
    @Override
    public BidTransaction findHighestBidByAuctionId(int auctionId) {
        String sql = "SELECT * FROM bid_transactions WHERE auction_id = ? ORDER BY bid_amount DESC LIMIT 1";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, auctionId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) return mapToBidTransaction(rs);
            return null;
        } catch (SQLException e) {
            throw new RuntimeException("Lỗi lấy bid cao nhất của Auction: " + e.getMessage());
        }
    }

    // ─── UPDATE ───────────────────────────────────────────────────
    @Override
    public void update(BidTransaction bid) {
        throw new UnsupportedOperationException("Cấm gian lận: Không thể sửa lịch sử đặt giá!");
    }

    // ─── DELETE ───────────────────────────────────────────────────
    @Override
    public void delete(int id) {
        throw new UnsupportedOperationException("Cấm gian lận: Không thể sửa lịch sử đặt giá!");
    }

    // ─── HELPER: ResultSet → BidTransaction ───────────────────────
    private BidTransaction mapToBidTransaction(ResultSet rs) throws SQLException {
        int id           = rs.getInt("id");
        int auctionId    = rs.getInt("auction_id");
        int bidderId     = rs.getInt("bidder_id");
        double bidAmount = rs.getDouble("bid_amount");

        // Hồi sinh Timestamp từ Database thành java.time.LocalDateTime
        LocalDateTime bidTime = rs.getTimestamp("bid_time").toLocalDateTime();

        // Khởi tạo Model (Đảm bảo Class BidTransaction của bạn có Constructor/Setter khớp thế này nhé)
        BidTransaction bid = new BidTransaction();
        bid.setId(id);
        bid.setAuctionId(auctionId);
        bid.setBidderId(bidderId);
        bid.setBidAmount(bidAmount);
        bid.setBidTime(bidTime);

        return bid;
    }
}
