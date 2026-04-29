package common.models.auction;

import common.models.user.Bidder;

import java.time.LocalDateTime;

public class BidTransaction {
    private String id;           // Đổi từ transactionId sang id
    private String auctionId;
    private String bidderId;
    private double bidAmount;
    private LocalDateTime bidTime;

    private Bidder bidder;

    // 1. Constructor rỗng (BẮT BUỘC phải có cho DAO)
    public BidTransaction() {
    }

    // 2. Constructor có tham số (Dùng khi Client tạo mới 1 lượt Bid)
    public BidTransaction(String id, String auctionId, String bidderId, double bidAmount) {
        this.id = id;
        this.auctionId = auctionId;
        this.bidderId = bidderId;
        this.bidAmount = bidAmount;
        this.bidTime = LocalDateTime.now();
    }

    // ─── GETTERS & SETTERS (Đã bổ sung đầy đủ) ───────────────────

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getAuctionId() {
        return auctionId;
    }

    public void setAuctionId(String auctionId) {
        this.auctionId = auctionId;
    }

    public String getBidderId() {
        return bidderId;
    }

    public void setBidderId(String bidderId) {
        this.bidderId = bidderId;
    }

    public double getBidAmount() {
        return bidAmount;
    }

    public void setBidAmount(double bidAmount) {
        this.bidAmount = bidAmount;
    }

    public LocalDateTime getBidTime() {
        return bidTime;
    }

    public void setBidTime(LocalDateTime bidTime) {
        this.bidTime = bidTime;
    }

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