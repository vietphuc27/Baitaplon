package server.manager;

import common.models.auction.Auction;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class AuctionManager {
    private static AuctionManager instance;

    private final List<Auction> activeAuctions;

    private AuctionManager() {
        activeAuctions = new ArrayList<>();
    }

    public static AuctionManager getInstance() {
        if (instance == null) {
            synchronized (AuctionManager.class) {
                if (instance == null) {
                    instance = new AuctionManager();
                }
            }
        }
        return instance;
    }

    public synchronized void addAuction(Auction auction) {
        if (auction == null) {
            return;
        }
        for (int i = 0; i < activeAuctions.size(); i++) {
            if (activeAuctions.get(i).getAuctionId() == auction.getAuctionId()) {
                activeAuctions.set(i, auction);
                return;
            }
        }
        activeAuctions.add(auction);
        System.out.println("AuctionManager: loaded auction ID " + auction.getAuctionId());
    }

    public synchronized List<Auction> getAllActiveAuctions() {
        return activeAuctions;
    }

    public synchronized Auction getAuctionById(int auctionId) {
        for (Auction a : activeAuctions) {
            if (a.getAuctionId() == auctionId) {
                return a;
            }
        }
        return null;
    }

    public synchronized List<Auction> getRunningAuctions() {
        return activeAuctions.stream()
                .filter(a -> a.getStatus() == common.models.auction.AuctionStatus.OPEN)
                .collect(Collectors.toList());
    }
}
