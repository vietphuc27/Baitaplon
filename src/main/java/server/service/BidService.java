package server.service;

import common.exceptions.AuctionClosedException;
import common.exceptions.InvalidBidException;
import common.models.auction.Auction;
import common.models.auction.AuctionStatus;
import common.models.auction.BidTransaction;
import common.models.user.Bidder;
import server.manager.AuctionManager;
import server.repository.dao.AuctionDAO;
import server.repository.dao.BidTransactionDAO;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
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
        Auction auction = auctionManager.getAuctionById(auctionId);
        if (auction == null) {
            auction = auctionDAO.findById(auctionId)
                    .orElseThrow(() -> new IllegalArgumentException("Khong tim thay auction: " + auctionId));
            auctionManager.addAuction(auction);
        }

        return placeBid(auction, bidder, amount);
    }

    public BidTransaction placeBid(Auction auction, Bidder bidder, double amount) {
        if (auction == null) {
            throw new IllegalArgumentException("Auction khong duoc null");
        }
        if (bidder == null) {
            throw new IllegalArgumentException("Bidder khong duoc null");
        }

        bidLock.lock();
        try {
            validateBid(auction, bidder, amount);

            BidTransaction bid = new BidTransaction(
                    UUID.randomUUID().toString(),
                    auction.getAuctionId(),
                    bidder.getId(),
                    amount
            );
            bid.setBidTime(LocalDateTime.now());
            bid.setBidder(bidder);

            boolean accepted = auction.processBid(bid);
            if (!accepted) {
                throw new InvalidBidException("Bid khong hop le hoac auction khong o trang thai RUNNING");
            }

            bidTransactionDAO.save(bid);
            auctionDAO.update(auction);
            return bid;
        } finally {
            bidLock.unlock();
        }
    }

    public List<BidTransaction> getAuctionBidHistory(String auctionId) {
        return bidTransactionDAO.findByAuctionId(auctionId);
    }

    public List<BidTransaction> getBidderBidHistory(String bidderId) {
        return bidTransactionDAO.findByBidderId(bidderId);
    }

    public BidTransaction getCurrentHighestBid(String auctionId) {
        return bidTransactionDAO.findHighestBidByAuctionId(auctionId);
    }

    private void validateBid(Auction auction, Bidder bidder, double amount) {
        if (amount <= 0) {
            throw new InvalidBidException("So tien bid phai lon hon 0");
        }

        if (auction.getStatus() != AuctionStatus.RUNNING && auction.getStatus() != AuctionStatus.OPEN) {
            throw new AuctionClosedException("Auction da dong, khong the dat gia");
        }

        if (auction.isClosed()) {
            throw new AuctionClosedException("Auction da dong, khong the dat gia");
        }

        if (amount <= auction.getCurrentHighestBid()) {
            throw new InvalidBidException("Gia bid phai cao hon gia hien tai");
        }

        if (bidder.getWallet() == null || bidder.getWallet().getBalance() < amount) {
            throw new InvalidBidException("So du khong du de dat gia");
        }
    }
}
