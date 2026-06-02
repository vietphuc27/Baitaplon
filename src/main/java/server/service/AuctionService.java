package server.service;

import common.models.auction.Auction;
import common.models.auction.AuctionStatus;
import common.models.item.Item;
import common.models.user.Bidder;
import common.models.user.Seller;
import common.models.user.User;
import server.manager.AuctionManager;
import server.config.DatabaseConnection;
import server.repository.AuctionDAO;
import server.repository.UserDAO;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

public class AuctionService {
    // Khoa toan cuc de tranh refresh trung lap khi nhieu luong chay cung luc
    private static final ReentrantLock REFRESH_LOCK = new ReentrantLock();

    // Phu thuoc chinh: DAO, manager in-memory, service item, DAO user
    private final AuctionDAO auctionDAO;
    private final AuctionManager auctionManager;
    private final ItemService itemService;
    private final UserDAO userDAO;

    public AuctionService(ItemService itemService) {
        this.auctionDAO = new AuctionDAO();
        this.itemService = itemService;
        this.auctionManager = AuctionManager.getInstance();
        this.userDAO = new UserDAO();
        bootstrapAuctionsFromDatabase();
    }

    // ==================== NGHIEP VU TAO/KET THUC AUCTION ====================
    // Tao phien dau gia moi sau khi validate seller/item/time
    public Auction createAuction(String sellerId, int itemId, LocalDateTime startTime, LocalDateTime endTime) {
        String normalizedSellerId = requireText(sellerId, "sellerId");
        int normalizedItemId = requirePositiveId(itemId, "itemId");
        LocalDateTime normalizedStartTime = requireTime(startTime, "startTime");
        LocalDateTime normalizedEndTime = requireTime(endTime, "endTime");

        if (!normalizedStartTime.isBefore(normalizedEndTime)) {
            throw new IllegalArgumentException("Thoi gian bat dau phai truoc thoi gian ket thuc");
        }
        if (!LocalDateTime.now().isBefore(normalizedStartTime)) {
            throw new IllegalArgumentException("Thoi gian bat dau phai sau hien tai");
        }

        Item item = itemService.findById(normalizedItemId)
                .orElseThrow(() -> new IllegalArgumentException("Khong tim thay san pham"));

        if (!normalizedSellerId.equals(item.getSellerId())) {
            throw new IllegalArgumentException("Nguoi ban khong co quyen tao auction cho san pham nay");
        }

        if (hasActiveAuctionForItem(normalizedItemId)) {
            throw new IllegalArgumentException("San pham nay da co auction dang chay hoac chua ket thuc");
        }

        int auctionId = generateAuctionId();
        Auction auction = new Auction(auctionId, item, normalizedSellerId, normalizedStartTime, normalizedEndTime);

        auctionDAO.save(auction);
        auctionManager.addAuction(auction);

        return auction;
    }

    // Seller ket thuc phien cua chinh minh
    public Auction endAuctionBySeller(String sellerId, int auctionId) {
        String normalizedSellerId = requireText(sellerId, "sellerId");
        int normalizedAuctionId = requirePositiveId(auctionId, "auctionId");

        Auction auction = auctionDAO.findById(normalizedAuctionId)
                .orElseThrow(() -> new IllegalArgumentException("Khong tim thay phien dau gia"));
        auctionManager.addAuction(auction);

        if (!normalizedSellerId.equals(auction.getSellerId())) {
            throw new IllegalArgumentException("Nguoi ban khong co quyen ket thuc phien dau gia nay");
        }

        if (auction.isClosed()) {
            throw new IllegalArgumentException("Phien dau gia da dong");
        }

        auction.setStatus(AuctionStatus.FINISHED);
        auctionDAO.update(auction);
        handleAuctionWinner(auction);
        auctionManager.addAuction(auction);
        return auction;
    }

    // ==================== CAP NHAT TRANG THAI THEO THOI GIAN ====================
    // Chay dinh ky: OPEN -> RUNNING, RUNNING -> FINISHED va xu ly winner
    public void refreshAuctionsStatus() {
        REFRESH_LOCK.lock();
        try {
            List<Auction> allAuctions = new ArrayList<>(auctionManager.getAllActiveAuctions());
            LocalDateTime now = LocalDateTime.now();

            for (Auction auction : allAuctions) {
                AuctionStatus beforeRefresh = auction.getStatus();

                if (auction.getStatus() == AuctionStatus.OPEN && !now.isBefore(auction.getStartTime())) {
                    auction.startAuction();
                    if (beforeRefresh != auction.getStatus()) {
                        System.out.println("He thong: Phien dau gia " + auction.getAuctionId() + " da BAT DAU.");
                    }
                }

                if (auction.getStatus() == AuctionStatus.RUNNING && !now.isBefore(auction.getEndTime())) {
                    AuctionStatus beforeEnd = auction.getStatus();
                    auction.endAuction();
                    if (beforeEnd != auction.getStatus() && auction.getStatus() == AuctionStatus.FINISHED) {
                        System.out.println("He thong: Phien dau gia " + auction.getAuctionId() + " da KET THUC.");
                        handleAuctionWinner(auction);
                    }
                }

                if (beforeRefresh != auction.getStatus()) {
                    auctionDAO.update(auction);
                }
            }
        } finally {
            REFRESH_LOCK.unlock();
        }
    }

    // Lay cac phien dang RUNNING de hien thi real-time
    public List<Auction> getLiveAuctions() {
        return auctionManager.getAllActiveAuctions().stream()
                .filter(auction -> auction.getStatus() == AuctionStatus.RUNNING)
                .collect(Collectors.toList());
    }

    // ==================== HAM NOI BO ====================
    // Nap toan bo auction tu DB vao manager khi khoi tao service
    private void bootstrapAuctionsFromDatabase() {
        try {
            for (Auction auction : auctionDAO.findAll()) {
                if (auctionManager.getAuctionById(auction.getAuctionId()) == null) {
                    auctionManager.addAuction(auction);
                }
            }
        } catch (RuntimeException e) {
            System.err.println("Khong the tai auction tu database: " + e.getMessage());
        }
    }

    // Xu ly thanh toan: tru tien bidder thang, cong tien seller, danh dau PAID
    private void handleAuctionWinner(Auction auction) {
        Integer winnerId = auction.getCurrentLeaderId();
        if (winnerId == null) {
            System.out.println("Phien dau gia ket thuc ma khong co nguoi dat gia.");
            return;
        }

        if (auction.getStatus() == AuctionStatus.PAID) {
            return;
        }

        double winningAmount = auction.getCurrentHighestBid();
        if (winningAmount <= 0) {
            System.out.println("Phien " + auction.getAuctionId() + " khong co gia thang hop le.");
            return;
        }

        User user = userDAO.findById(winnerId).orElse(null);
        if (!(user instanceof Bidder bidder) || bidder.getWallet() == null) {
            System.out.println("Khong tim thay vi cua nguoi thang " + winnerId + " de thanh toan.");
            return;
        }

        Seller seller = resolveSellerForPayment(auction);
        if (seller == null || seller.getWallet() == null) {
            System.out.println("Khong tim thay vi cua seller " + auction.getSellerId() + " de nhan tien.");
            return;
        }

        if (!bidder.getWallet().withdraw(winningAmount)) {
            System.out.println("Nguoi thang " + winnerId + " khong du so du de tru tien.");
            return;
        }
        if (!seller.getWallet().deposit(winningAmount)) {
            bidder.getWallet().deposit(winningAmount);
            System.out.println("Khong the cong tien cho seller " + auction.getSellerId() + ".");
            return;
        }

        Connection conn = null;
        AuctionStatus previousStatus = auction.getStatus();
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            userDAO.update(conn, bidder);
            userDAO.update(conn, seller);
            auction.setStatus(AuctionStatus.PAID);
            auctionDAO.update(conn, auction);
            conn.commit();
            System.out.println("Da thanh toan " + winningAmount + " tu bidder " + winnerId
                    + " cho seller " + auction.getSellerId() + " o phien " + auction.getAuctionId() + ".");
        } catch (RuntimeException | SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ignored) {
                }
            }
            if (conn == null && isCustomPaymentDao()) {
                persistPaymentWithoutTransaction(bidder, seller, auction);
                System.out.println("Da thanh toan " + winningAmount + " tu bidder " + winnerId
                        + " cho seller " + auction.getSellerId() + " o phien " + auction.getAuctionId() + ".");
                return;
            }
            bidder.getWallet().deposit(winningAmount);
            seller.getWallet().withdraw(winningAmount);
            auction.setStatus(previousStatus);
            throw new RuntimeException("Loi persist thanh toan winner: " + e.getMessage(), e);
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                } catch (SQLException ignored) {
                }
                try {
                    conn.close();
                } catch (SQLException ignored) {
                }
            }
        }
    }

    private Seller resolveSellerForPayment(Auction auction) {
        if (auction == null || auction.getSellerId() == null || auction.getSellerId().isBlank()) {
            return null;
        }
        String sellerId = auction.getSellerId().trim();
        try {
            int sellerUserId = Integer.parseInt(sellerId);
            User sellerUser = userDAO.findById(sellerUserId).orElse(null);
            if (sellerUser instanceof Seller seller) {
                return seller;
            }
        } catch (NumberFormatException e) {
            return resolveSellerByUsername(sellerId);
        }
        return resolveSellerByUsername(sellerId);
    }

    private Seller resolveSellerByUsername(String sellerId) {
        User sellerUser = userDAO.findByUsername(sellerId).orElse(null);
        return sellerUser instanceof Seller seller ? seller : null;
    }

    private boolean isCustomPaymentDao() {
        return auctionDAO.getClass() != AuctionDAO.class || userDAO.getClass() != UserDAO.class;
    }

    private void persistPaymentWithoutTransaction(Bidder bidder, Seller seller, Auction auction) {
        userDAO.update(bidder);
        userDAO.update(seller);
        auction.setStatus(AuctionStatus.PAID);
        auctionDAO.update(auction);
    }

    // Kiem tra 1 item da co phien chua dong hay chua
    private boolean hasActiveAuctionForItem(int itemId) {
        return auctionManager.getAllActiveAuctions().stream()
                .filter(auction -> auction.getItem() != null)
                .anyMatch(auction -> itemId == auction.getItem().getId() && !auction.isClosed());
    }

    // Validate chuoi bat buoc
    private String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " khong duoc de trong");
        }
        return value.trim();
    }

    // Validate id duong
    private int requirePositiveId(int value, String fieldName) {
        if (value <= 0) {
            throw new IllegalArgumentException(fieldName + " khong hop le");
        }
        return value;
    }

    // Sinh auction ID ngau nhien khong trung
    private int generateAuctionId() {
        int id;
        do {
            id = ThreadLocalRandom.current().nextInt(100000, 999999);
        } while (auctionDAO.findById(id).isPresent());
        return id;
    }

    // Validate thoi gian bat buoc
    private LocalDateTime requireTime(LocalDateTime value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " khong duoc de trong");
        }
        return value;
    }
}
