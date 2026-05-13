package common.models.auction;

import common.models.item.Item;
import common.models.user.Bidder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Auction {
    private int id;
    private Item item;
    private String sellerId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private double currentHighestBid;
    private AuctionStatus status;

    private Bidder currentLeader;
    private Integer currentLeaderId;
    private List<BidTransaction> bidHistory;

    public Auction() {
    }

    public Auction(int id, Item item, String sellerId, LocalDateTime startTime, LocalDateTime endTime) {
        this.id = id;
        this.item = item;
        this.sellerId = sellerId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.currentHighestBid = 0;
        this.status = AuctionStatus.OPEN;
        this.bidHistory = new ArrayList<>();
    }

    public void startAuction() {
        if (status == AuctionStatus.OPEN && LocalDateTime.now().isAfter(startTime)) {
            status = AuctionStatus.RUNNING;
            System.out.println("Auction started");
        }
    }

    public void endAuction() {
        if (status == AuctionStatus.RUNNING && LocalDateTime.now().isAfter(endTime)) {
            status = AuctionStatus.FINISHED;
            System.out.println("Auction ended");
        }
    }

    public boolean isClosed() {
        return this.status == AuctionStatus.FINISHED || this.status == AuctionStatus.PAID || this.status == AuctionStatus.CANCELED;
    }

    public boolean processBid(BidTransaction bid) {
        startAuction();
        endAuction();

        if (status != AuctionStatus.RUNNING) {
            return false;
        }

        double startingPrice = item != null ? item.getStartingPrice() : 0;
        double amount = bid.getBidAmount();

        if (amount < startingPrice) {
            return false;
        }

        if (currentHighestBid > 0 && amount <= currentHighestBid) {
            return false;
        }

        currentHighestBid = bid.getBidAmount();
        currentLeader = bid.getBidder();

        if (currentLeader != null) {
            currentLeaderId = currentLeader.getId();
        }

        bidHistory.add(bid);
        return true;
    }

    public int getAuctionId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public Item getItem() {
        return item;
    }

    public void setItem(Item item) {
        this.item = item;
    }

    public String getSellerId() {
        return sellerId;
    }

    public void setSellerId(String sellerId) {
        this.sellerId = sellerId;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public AuctionStatus getStatus() {
        return status;
    }

    public void setStatus(AuctionStatus status) {
        this.status = status;
    }

    public double getCurrentHighestBid() {
        return currentHighestBid;
    }

    public void setCurrentHighestBid(double currentHighestBid) {
        this.currentHighestBid = currentHighestBid;
    }

    public Bidder getCurrentLeader() {
        return currentLeader;
    }

    public void setCurrentLeader(Bidder currentLeader) {
        this.currentLeader = currentLeader;
    }

    public Integer getCurrentLeaderId() {
        if (currentLeader != null) {
            return currentLeader.getId();
        }
        return currentLeaderId;
    }

    public void setCurrentLeaderId(Integer currentLeaderId) {
        this.currentLeaderId = currentLeaderId;
    }

    public List<BidTransaction> getBidHistory() {
        return bidHistory;
    }
}
