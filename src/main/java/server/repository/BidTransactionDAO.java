package server.repository;

import common.models.auction.BidTransaction;
import server.config.DatabaseConnection;
import server.repository.dao.BidTransactionRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class BidTransactionDAO implements BidTransactionRepository {

    @Override
    public void save(BidTransaction bid) {
        String sql = "INSERT INTO bid_transactions (auction_id, bidder_id, bid_amount, bid_time) "
                + "VALUES (?, ?, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setInt(1, bid.getAuctionId());
            stmt.setInt(2, bid.getBidderId());
            stmt.setDouble(3, bid.getBidAmount());
            stmt.setTimestamp(4, Timestamp.valueOf(bid.getBidTime()));

            stmt.executeUpdate();

            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    bid.setId(generatedKeys.getInt(1));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Loi luu BidTransaction: " + e.getMessage());
        }
    }

    @Override
    public Optional<BidTransaction> findById(int id) {
        String sql = "SELECT * FROM bid_transactions WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, id);
            ResultSet rs = stmt.executeQuery();

            if (rs.next())
                return Optional.of(mapToBidTransaction(rs));
            return Optional.empty();
        } catch (SQLException e) {
            throw new RuntimeException("Loi tim BidTransaction theo ID: " + e.getMessage());
        }
    }

    @Override
    public List<BidTransaction> findAll() {
        String sql = "SELECT * FROM bid_transactions ORDER BY bid_time DESC";
        List<BidTransaction> list = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql);
                ResultSet rs = stmt.executeQuery()) {

            while (rs.next())
                list.add(mapToBidTransaction(rs));
            return list;
        } catch (SQLException e) {
            throw new RuntimeException("Loi lay danh sach tat ca BidTransaction: " + e.getMessage());
        }
    }

    @Override
    public List<BidTransaction> findByAuctionId(int auctionId) {
        String sql = "SELECT * FROM bid_transactions WHERE auction_id = ? ORDER BY id ASC";
        List<BidTransaction> list = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, auctionId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next())
                list.add(mapToBidTransaction(rs));
            return list;
        } catch (SQLException e) {
            throw new RuntimeException("Loi lay lich su bid cua Auction: " + e.getMessage());
        }
    }

    @Override
    public List<BidTransaction> findByBidderId(int bidderId) {
        String sql = "SELECT * FROM bid_transactions WHERE bidder_id = ? ORDER BY bid_time DESC";
        List<BidTransaction> list = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, bidderId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next())
                list.add(mapToBidTransaction(rs));
            return list;
        } catch (SQLException e) {
            throw new RuntimeException("Loi lay lich su bid cua Bidder: " + e.getMessage());
        }
    }

    @Override
    public BidTransaction findHighestBidByAuctionId(int auctionId) {
        String sql = "SELECT * FROM bid_transactions WHERE auction_id = ? ORDER BY bid_amount DESC LIMIT 1";

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, auctionId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next())
                return mapToBidTransaction(rs);
            return null;
        } catch (SQLException e) {
            throw new RuntimeException("Loi lay bid cao nhat cua Auction: " + e.getMessage());
        }
    }

    @Override
    public void update(BidTransaction bid) {
        throw new UnsupportedOperationException("Cam gian lan: Khong the sua lich su dat gia!");
    }

    @Override
    public void delete(int id) {
        throw new UnsupportedOperationException("Cam gian lan: Khong the sua lich su dat gia!");
    }

    private BidTransaction mapToBidTransaction(ResultSet rs) throws SQLException {
        int id = rs.getInt("id");
        int auctionId = rs.getInt("auction_id");
        int bidderId = rs.getInt("bidder_id");
        double bidAmount = rs.getDouble("bid_amount");
        LocalDateTime bidTime = rs.getTimestamp("bid_time").toLocalDateTime();

        BidTransaction bid = new BidTransaction();
        bid.setId(id);
        bid.setAuctionId(auctionId);
        bid.setBidderId(bidderId);
        bid.setBidAmount(bidAmount);
        bid.setBidTime(bidTime);
        return bid;
    }
}
