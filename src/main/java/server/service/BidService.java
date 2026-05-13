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
import server.repository.UserDAO;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public class BidService {
    private static final ConcurrentHashMap<Integer, ReentrantLock> AUCTION_LOCKS = new ConcurrentHashMap<>();

    private final AuctionManager auctionManager;
    private final AuctionDAO auctionDAO;
    private final BidTransactionDAO bidTransactionDAO;
    private final UserDAO userDAO;

    public BidService() {
        this(AuctionManager.getInstance(), new AuctionDAO(), new BidTransactionDAO(), new UserDAO());
    }

    public BidService(AuctionManager auctionManager, AuctionDAO auctionDAO, BidTransactionDAO bidTransactionDAO) {
        this(auctionManager, auctionDAO, bidTransactionDAO, new UserDAO());
    }

    public BidService(AuctionManager auctionManager, AuctionDAO auctionDAO, BidTransactionDAO bidTransactionDAO, UserDAO userDAO) {
        this.auctionManager = auctionManager;
        this.auctionDAO = auctionDAO;
        this.bidTransactionDAO = bidTransactionDAO;
        this.userDAO = userDAO;
    }

    public BidTransaction placeBid(String auctionId, Bidder bidder, double amount) {
        int auctionIdInt;
        try {
            auctionIdInt = Integer.parseInt(auctionId);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("ID không hợp lệ: " + auctionId);
        }

        Auction auction = auctionManager.getAuctionById(auctionIdInt);
        if (auction == null) {
            auction = auctionDAO.findById(auctionIdInt)
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy phiên đấu giá: " + auctionId));
            auctionManager.addAuction(auction);
        }

        return placeBid(auction, bidder, amount);
    }

    public BidTransaction placeBid(Auction auction, Bidder bidder, double amount) {
        if (auction == null) {
            throw new IllegalArgumentException("Không tìm thấy phiên đấu giá");
        }
        if (bidder == null) {
            throw new IllegalArgumentException("Không tìm thấy Bidder");
        }

        ReentrantLock bidLock = AUCTION_LOCKS.computeIfAbsent(auction.getAuctionId(), id -> new ReentrantLock());
        bidLock.lock();
        try {
            validateBid(auction, bidder, amount);

            if (bidder.getWallet() == null) {
                throw new InvalidBidException("Không đủ số dư");
            }
            if (!bidder.getWallet().withdraw(amount)) {
                throw new InvalidBidException("Không đủ số dư");
            }

            if (!persistBidder(bidder)) {
                bidder.getWallet().deposit(amount);
                throw new InvalidBidException("Không cập nhật được số dư");
            }

            double previousHighestBid = auction.getCurrentHighestBid();
            Bidder previousLeader = auction.getCurrentLeader();
            Integer previousLeaderId = auction.getCurrentLeaderId();

            BidTransaction bid = new BidTransaction(
                    0,
                    auction.getAuctionId(),
                    bidder.getId(),
                    amount
            );
            bid.setBidTime(LocalDateTime.now());
            bid.setBidder(bidder);

            boolean accepted = auction.processBid(bid);
            if (!accepted) {
                bidder.getWallet().deposit(amount);
                persistBidder(bidder);
                throw new InvalidBidException("Giá không hợp lệ hoặc phiên đấu giá đã đóng");
            }

            try {
                auctionDAO.update(auction);
                bidTransactionDAO.save(bid);
                return bid;
            } catch (RuntimeException e) {
                rollbackAuctionState(auction, previousHighestBid, previousLeader, previousLeaderId, bid);
                bidder.getWallet().deposit(amount);
                persistBidder(bidder);
                try {
                    auctionDAO.update(auction);
                } catch (RuntimeException ignored) {
                    // If rollback persistence also fails, keep the original failure visible.
                }
                throw e;
            }
        } finally {
            bidLock.unlock();
        }
    }

    public List<BidTransaction> getAuctionBidHistory(String auctionId) {
        int auctionIdInt;
        try {
            auctionIdInt = Integer.parseInt(auctionId);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("ID phiên đấu giá không hợp lệ: " + auctionId);
        }
        return bidTransactionDAO.findByAuctionId(auctionIdInt);
    }

    public List<BidTransaction> getBidderBidHistory(String bidderId) {
        int bidderIdInt;
        try {
            bidderIdInt = Integer.parseInt(bidderId);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Bidder ID không hợp lệ: " + bidderId);
        }
        return bidTransactionDAO.findByBidderId(bidderIdInt);
    }

    public BidTransaction getCurrentHighestBid(String auctionId) {
        int auctionIdInt;
        try {
            auctionIdInt = Integer.parseInt(auctionId);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("ID phiên đấu giá không hợp lệ: " + auctionId);
        }
        return bidTransactionDAO.findHighestBidByAuctionId(auctionIdInt);
    }

    private void validateBid(Auction auction, Bidder bidder, double amount) {
        if (amount <= 0) {
            throw new InvalidBidException("Giá không hợp lệ");
        }

        if (auction.getItem() == null) {
            throw new InvalidBidException("Phiên đấu giá không hợp lệ");
        }

        double startingPrice = auction.getItem().getStartingPrice();
        if (auction.getStatus() != AuctionStatus.RUNNING && auction.getStatus() != AuctionStatus.OPEN) {
            throw new AuctionClosedException("Phiên đấu giá đã đóng");
        }

        if (auction.isClosed()) {
            throw new AuctionClosedException("Phiên đấu giá đã đóng");
        }

        if (amount < startingPrice) {
            throw new InvalidBidException("Giá đặt phải lớn hơn giá ban đầu");
        }

        if (auction.getCurrentHighestBid() > 0) {
            if (amount <= auction.getCurrentHighestBid()) {
                throw new InvalidBidException("Giá đặt phải lớn hơn giá cao nhất");
            }
        }

        if (bidder.getWallet() == null || bidder.getWallet().getBalance() < amount) {
            throw new InvalidBidException("Không đủ số dư");
        }
    }

    private boolean persistBidder(Bidder bidder) {
        try {
            userDAO.update(bidder);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private void rollbackAuctionState(Auction auction, double previousHighestBid, Bidder previousLeader, Integer previousLeaderId, BidTransaction bid) {
        auction.getBidHistory().remove(bid);
        auction.setCurrentHighestBid(previousHighestBid);
        auction.setCurrentLeader(previousLeader);
        auction.setCurrentLeaderId(previousLeaderId);
    }
}
