package auction;

public interface AuctionObserver {
    void updateCurrentBid(BidTransaction bid);
    void updateAuctionStatus(AuctionStatus status);
}
