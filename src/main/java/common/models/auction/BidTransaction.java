package common.models.auction;

import common.models.user.Bidder;

import java.time.LocalDateTime;

public class BidTransaction {
    private int id;           // Đổi từ transactionId sang id
    private int auctionId;
    private int bidderId;
    private double bidAmount;
    private LocalDateTime bidTime;

    private Bidder bidder;

    // 1. Constructor rỗng (BẮT BUỘC phải có cho DAO)
    public BidTransaction() {
    }

    // 2. Constructor có tham số (Dùng khi Client tạo mới 1 lượt Bid)
    public BidTransaction(int id, int auctionId, int bidderId, double bidAmount) {
        this.id = id;
        this.auctionId = auctionId;
        this.bidderId = bidderId;
        this.bidAmount = bidAmount;
        this.bidTime = LocalDateTime.now();
    }

    // ─── GETTERS & SETTERS (Đã bổ sung đầy đủ) ───────────────────

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getAuctionId() {
        return auctionId;
    }

    public void setAuctionId(int auctionId) {
        this.auctionId = auctionId;
    }

    public int getBidderId() {
        return bidderId;
    }

    public void setBidderId(int bidderId) {
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
