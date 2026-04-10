package user;

import auction.Auction;
import auction.AuctionObserver;
import auction.AuctionStatus;
import auction.BidTransaction;

import java.util.ArrayList;
import java.util.List;

public class Bidder extends User implements AuctionObserver {
    private Wallet wallet;
    private List<BidTransaction> bidHistory;
    public Bidder(String id, String username, String email, String password){
        super(id, username, email, password, "BIDDER");
        this.wallet = new Wallet();
        this.bidHistory = new ArrayList<>();
    }
    public boolean placeBid(Auction auction, double amount){
        return true;
        //thêm logic sau
    }
    public Wallet getWallet() {
        return wallet;
    }

    @Override
    public void updateCurrentBid(BidTransaction bid){
        System.out.println("Giá mới đã được cập nhật");
        //
    }
    @Override
    public void updateAuctionStatus(AuctionStatus status){
        System.out.println("Phiên đấu giá đã thay đổi sang trạng thái: "+ status);
    }
}
