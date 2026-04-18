package models.user;
import exceptions.*;

import models.auction.Auction;
import models.auction.AuctionObserver;
import models.auction.AuctionStatus;
import models.auction.BidTransaction;
import exceptions.AuctionClosedException;
import exceptions.InvalidBidException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;


public class Bidder extends User implements AuctionObserver {
    private Wallet wallet;
    private List<BidTransaction> bidHistory;
    private final ReentrantLock lock = new ReentrantLock();


    public Bidder(String id, String username, String email, String password){
        super(id, username, email, password, "BIDDER");
        this.wallet = new Wallet();
        this.bidHistory = new ArrayList<>();
    }
    
    public boolean placeBid(Auction auction, double amount){
        if (lock.tryLock()){
            try {
                if (auction.getStatus() == null){
                    throw new AuthenticationException("lỗi dữ liễu");
                }
                if (auction.isClosed()){
                    throw new AuctionClosedException("Phiên đấu giá đã đóng");
                }
                if (amount <= auction.getCurrentHighestBid()){
                    throw new InvalidBidException("Hãy đặt giá cao hơn hiện tại");
                }
                if (amount > auction.getCurrentHighestBid() && this.wallet.withdraw(amount)){
                    auction.setCurrentHighestBid(amount);
                    return true;
                } 
                
            } catch (AuthenticationException e) {
                System.out.println(e.getMessage());
                return false;

            } catch (AuctionClosedException e){
                System.out.println(e.getMessage());
                return false;
                
            } catch (InvalidBidException e){
                System.out.println(e.getMessage());
            }
            finally {
                lock.unlock();
            }
        }
        return false;
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
