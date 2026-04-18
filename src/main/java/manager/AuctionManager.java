package manager;

import models.auction.Auction;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class AuctionManager {
    private static AuctionManager instance;

    private List<Auction> activeAuctions;

    private AuctionManager() {
        activeAuctions = new ArrayList<>();
    }

    public static AuctionManager getInstance() {
        if (instance == null) {
            instance = new AuctionManager();
        }
        return instance;
    }
    public void addAuction(Auction auction) {
        activeAuctions.add(auction);
        System.out.println("Sàn giao dịch: Đã thêm phiên đấu giá ID: " + auction.getAuctionIdId());
    }

    public List<Auction> getAllActiveAuctions() {
        return activeAuctions;
    }

    public Auction getAuctionById(String auctionId) {
        for (Auction a : activeAuctions) {
            if (a.getAuctionIdId().equals(auctionId)) {
                return a;
            }
        }
        return null;
    }

    public List<Auction> getRunningAuctions() {
        return activeAuctions.stream()
                .filter(a -> a.getStatus() == models.auction.AuctionStatus.OPEN)
                .collect(Collectors.toList());
    }
}