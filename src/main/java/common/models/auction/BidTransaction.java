package common.models.auction;

import common.models.user.Bidder;

import java.time.LocalDateTime;

public class BidTransaction {
    private String transactionId;
    private String auctionId;
    private String bidderId;
    private double bidAmount;
    private LocalDateTime timestamp;
    public BidTransaction(String transactionId, String auctionId, String bidderId, double bidAmount) {
        this.transactionId = transactionId;
        this.auctionId = auctionId;
        this.bidderId = bidderId;
        this.bidAmount = bidAmount;
        this.timestamp = LocalDateTime.now();
    }
    public double getBidAmount() {
        return bidAmount;
    }
    public String getBidderId() {
        return bidderId;
    }
    public String getAuctionId() {
        return auctionId;
    }
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    private Bidder bidder;
    public Bidder getBidder() {
        return bidder;
    }
    public void setBidder(Bidder bidder) {
        this.bidder = bidder;
    }
    public String getDetails() {
        return "Bid: " + bidAmount + " by " + bidderId;
    }
}
