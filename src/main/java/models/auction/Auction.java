package models.auction;

import models.user.Bidder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Auction {
    private String auctionId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private double currentHighestBid;
    private AuctionStatus status;
    private Bidder currentLeader;
    private List<BidTransaction> bidHistory;

    public Auction(String auctionId, LocalDateTime startTime, LocalDateTime endTime){
        this.auctionId = auctionId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.currentHighestBid = 0;
        this.status = AuctionStatus.OPEN;
        this.bidHistory = new ArrayList<>();
    }
    public void startAuction(){
        if (status == AuctionStatus.OPEN && LocalDateTime.now().isAfter(startTime)){
            status = AuctionStatus.RUNNING;
            System.out.println("Phiên đấu giá bắt đầu");
        }
    }
    public void endAuction(){
        if (status == AuctionStatus.RUNNING && LocalDateTime.now().isAfter(endTime)){
            status = AuctionStatus.FINISHED;
            System.out.println("Phiên đấu giá kết thúc");
        }
    }
    public boolean processBid(BidTransaction bid) {
        startAuction();
        endAuction();

        if (status != AuctionStatus.RUNNING) return false;

        if (bid.getBidAmount() <= currentHighestBid) return false;

        currentHighestBid = bid.getBidAmount();
        currentLeader = bid.getBidder();
        bidHistory.add(bid);

        return true;
    }
    public AuctionStatus getStatus() {
        return status;
    }
    public double getCurrentHighestBid() {
        return currentHighestBid;
    }
    public Bidder getCurrentLeader() {
        return currentLeader;
    }
    public List<BidTransaction> getBidHistory() {
        return bidHistory;
    }

    public String getAuctionIdId() {
        return auctionId;
    }
}
