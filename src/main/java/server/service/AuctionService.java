package server.service;

import common.models.auction.Auction;
import common.models.auction.AuctionStatus;
import common.models.item.Item;
import server.manager.AuctionManager;
import server.repository.dao.AuctionDAO;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class AuctionService {
    private final AuctionDAO auctionDAO;
    private final AuctionManager auctionManager;
    private final ItemService itemService;

    public AuctionService(ItemService itemService) {
        this.auctionDAO = new AuctionDAO();
        this.itemService = itemService;
        this.auctionManager = AuctionManager.getInstance();
        bootstrapAuctionsFromDatabase();
    }

    public Auction createAuction(String sellerId, String itemId, LocalDateTime startTime, LocalDateTime endTime) {
        String normalizedSellerId = requireText(sellerId, "sellerId");
        String normalizedItemId = requireText(itemId, "itemId");
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

        String auctionId = UUID.randomUUID().toString();
        Auction auction = new Auction(auctionId, item, normalizedSellerId, normalizedStartTime, normalizedEndTime);

        auctionDAO.save(auction);
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
        if (auction.getCurrentLeaderId() != null) {
            System.out.println("Chuc mung nguoi dung " + auction.getCurrentLeaderId() + " da thang dau gia!");
        } else {
            System.out.println("Phien dau gia ket thuc ma khong co nguoi dat gia.");
        }
    }

    private boolean hasActiveAuctionForItem(String itemId) {
        return auctionManager.getAllActiveAuctions().stream()
                .filter(auction -> auction.getItem() != null)
                .anyMatch(auction -> itemId.equals(auction.getItem().getId()) && !auction.isClosed());
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " khong duoc de trong");
        }
        return value.trim();
    }

    private LocalDateTime requireTime(LocalDateTime value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " khong duoc de trong");
        }
        return value;
    }
}
