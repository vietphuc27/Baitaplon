package server.service;

import common.models.auction.Auction;
import common.models.auction.AuctionStatus;
import common.models.item.Item;
import common.models.user.Bidder;
import common.models.user.User;
import server.manager.AuctionManager;
import server.repository.AuctionDAO;
import server.repository.UserDAO;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class AuctionService {
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

    public Auction createAuction(String sellerId, int itemId, LocalDateTime startTime, LocalDateTime endTime) {
        String normalizedSellerId = requireText(sellerId, "sellerId");
        int normalizedItemId = requirePositiveId(itemId, "itemId");
        LocalDateTime normalizedStartTime = requireTime(startTime, "startTime");
        LocalDateTime normalizedEndTime = requireTime(endTime, "endTime");

        if (!normalizedStartTime.isBefore(normalizedEndTime)) {
            throw new IllegalArgumentException("Thoi gian bat dau phai truoc thoi gian ket thuc");
        }
        if (normalizedStartTime.isBefore(LocalDateTime.now().minusMinutes(1))) {
            throw new IllegalArgumentException("Thoi gian bat dau khong duoc o qua khu");
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

    public void refreshAuctionsStatus() {
        List<Auction> allAuctions = new ArrayList<>(auctionManager.getAllActiveAuctions());
        LocalDateTime now = LocalDateTime.now();

        for (Auction auction : allAuctions) {
            AuctionStatus beforeRefresh = auction.getStatus();
            boolean changed = false;

            if (auction.getStatus() == AuctionStatus.OPEN && !now.isBefore(auction.getStartTime())) {
                auction.startAuction();
                changed = true;
                System.out.println("He thong: Phien dau gia " + auction.getAuctionId() + " da BAT DAU.");
            }

            if (auction.getStatus() == AuctionStatus.RUNNING && !now.isBefore(auction.getEndTime())) {
                auction.endAuction();
                changed = true;
                System.out.println("He thong: Phien dau gia " + auction.getAuctionId() + " da KET THUC.");
                handleAuctionWinner(auction);
            }

            if (changed || beforeRefresh != auction.getStatus()) {
                auctionDAO.update(auction);
            }
        }
    }

    public List<Auction> getLiveAuctions() {
        return auctionManager.getAllActiveAuctions().stream()
                .filter(auction -> auction.getStatus() == AuctionStatus.RUNNING)
                .collect(Collectors.toList());
    }

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

        if (!bidder.getWallet().withdraw(winningAmount)) {
            System.out.println("Nguoi thang " + winnerId + " khong du so du de tru tien.");
            return;
        }

        try {
            userDAO.update(bidder);
            auction.setStatus(AuctionStatus.PAID);
            auctionDAO.update(auction);
            System.out.println("Da tru " + winningAmount + " tu nguoi thang " + winnerId
                    + " cho phien " + auction.getAuctionId() + ".");
        } catch (RuntimeException e) {
            bidder.getWallet().deposit(winningAmount);
            try {
                userDAO.update(bidder);
            } catch (RuntimeException ignored) {
            }
            throw e;
        }
    }

    private boolean hasActiveAuctionForItem(int itemId) {
        return auctionManager.getAllActiveAuctions().stream()
                .filter(auction -> auction.getItem() != null)
                .anyMatch(auction -> itemId == auction.getItem().getId() && !auction.isClosed());
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " khong duoc de trong");
        }
        return value.trim();
    }

    private int requirePositiveId(int value, String fieldName) {
        if (value <= 0) {
            throw new IllegalArgumentException(fieldName + " khong hop le");
        }
        return value;
    }

    private int generateAuctionId() {
        int id;
        do {
            id = ThreadLocalRandom.current().nextInt(100000, 999999);
        } while (auctionDAO.findById(id).isPresent());
        return id;
    }

    private LocalDateTime requireTime(LocalDateTime value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " khong duoc de trong");
        }
        return value;
    }
}
