package server.service;

import common.exceptions.AuctionClosedException;
import common.exceptions.InvalidBidException;
import common.models.auction.Auction;
import common.models.auction.AuctionStatus;
import common.models.auction.BidTransaction;
import common.models.user.Bidder;
import server.manager.AuctionManager;
import server.repository.AuctionDAO;
import server.repository.BidTransactionDAO;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

public class BidService {
    private final AuctionManager auctionManager;
    private final AuctionDAO auctionDAO;
    private final BidTransactionDAO bidTransactionDAO;
    private final ReentrantLock bidLock;

    public BidService() {
        this.auctionManager = AuctionManager.getInstance();
        this.auctionDAO = new AuctionDAO();
        this.bidTransactionDAO = new BidTransactionDAO();
        this.bidLock = new ReentrantLock();
    }

    public BidService(AuctionManager auctionManager, AuctionDAO auctionDAO, BidTransactionDAO bidTransactionDAO) {
        this.auctionManager = auctionManager;
        this.auctionDAO = auctionDAO;
        this.bidTransactionDAO = bidTransactionDAO;
        this.bidLock = new ReentrantLock();
    }

    public BidTransaction placeBid(String auctionId, Bidder bidder, double amount) {
        int auctionIdInt;
        try {
            auctionIdInt = Integer.parseInt(auctionId);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid auction ID: " + auctionId);
        }
        
        Auction auction = auctionManager.getAuctionById(auctionIdInt);
        if (auction == null) {
            auction = auctionDAO.findById(auctionIdInt)
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy auction: " + auctionId));
            auctionManager.addAuction(auction);
        }

        return placeBid(auction, bidder, amount);
    }

    public BidTransaction placeBid(Auction auction, Bidder bidder, double amount) {
        if (auction == null) {
            throw new IllegalArgumentException("Không tìm thấy phiên đấu giá");
        }
        if (bidder == null) {
            throw new IllegalArgumentException("Không xác định được người đấu giá");
        }

        bidLock.lock();
        try {
            validateBid(auction, bidder, amount);

            BidTransaction bid = new BidTransaction(
                    0, // ID sẽ được generate bởi database
                    auction.getAuctionId(),
                    bidder.getId(),
                    amount
            );
            bid.setBidTime(LocalDateTime.now());
            bid.setBidder(bidder);

            boolean accepted = auction.processBid(bid);
            if (!accepted) {
                throw new InvalidBidException("Đấu giá thất bại: giá không hợp lệ hoặc phiên đấu giá không còn mở");
            }

            bidTransactionDAO.save(bid);
            auctionDAO.update(auction);
            return bid;
        } finally {
            bidLock.unlock();
        }
    }

    public List<BidTransaction> getAuctionBidHistory(String auctionId) {
        int auctionIdInt;
        try {
            auctionIdInt = Integer.parseInt(auctionId);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid auction ID: " + auctionId);
        }
        return bidTransactionDAO.findByAuctionId(auctionIdInt);
    }

    public List<BidTransaction> getBidderBidHistory(String bidderId) {
        int bidderIdInt;
        try {
            bidderIdInt = Integer.parseInt(bidderId);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid bidder ID: " + bidderId);
        }
        return bidTransactionDAO.findByBidderId(bidderIdInt);
    }

    public BidTransaction getCurrentHighestBid(String auctionId) {
        int auctionIdInt;
        try {
            auctionIdInt = Integer.parseInt(auctionId);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid auction ID: " + auctionId);
        }
        return bidTransactionDAO.findHighestBidByAuctionId(auctionIdInt);
    }

    private void validateBid(Auction auction, Bidder bidder, double amount) {
        if (amount <= 0) {
            throw new InvalidBidException("Số tiền không hợp lệ");
        }

        if (auction.getStatus() != AuctionStatus.RUNNING && auction.getStatus() != AuctionStatus.OPEN) {
            throw new AuctionClosedException("Phiên đấu giá đã đóng, không thể đặt giá");
        }

        if (auction.isClosed()) {
            throw new AuctionClosedException("Phiên đấu giá đã đóng, không thể đặt giá");
        }

        if (amount <= auction.getCurrentHighestBid()) {
            throw new InvalidBidException("Giá đặt không hợp lệ");
        }

        if (bidder.getWallet() == null || bidder.getWallet().getBalance() < amount) {
            throw new InvalidBidException("Số dư không đủ để đặt giá");
        }
    }
}
