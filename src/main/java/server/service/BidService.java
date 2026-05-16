package server.service;

import common.exceptions.AuctionClosedException;
import common.exceptions.InvalidBidException;
import common.models.auction.Auction;
import common.models.auction.AuctionStatus;
import common.models.auction.BidTransaction;
import common.models.user.Bidder;
import common.models.user.User;
import server.manager.AuctionManager;
import server.manager.AutoBidManager;
import server.repository.AuctionDAO;
import server.repository.BidTransactionDAO;
import server.repository.UserDAO;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

public class BidService {
    private static final ConcurrentHashMap<Integer, ReentrantLock> AUCTION_LOCKS = new ConcurrentHashMap<>();
    private static final ExecutorService AUTO_BID_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "auto-bid-processor");
        t.setDaemon(true);
        return t;
    });

    private final AuctionManager auctionManager;
    private final AuctionDAO auctionDAO;
    private final BidTransactionDAO bidTransactionDAO;
    private final UserDAO userDAO;
    private final AutoBidManager autoBidManager;

    public BidService() {
        this(AuctionManager.getInstance(), new AuctionDAO(), new BidTransactionDAO(), new UserDAO());
    }

    public BidService(AuctionManager auctionManager, AuctionDAO auctionDAO, BidTransactionDAO bidTransactionDAO) {
        this(auctionManager, auctionDAO, bidTransactionDAO, new UserDAO());
    }

    public BidService(AuctionManager auctionManager, AuctionDAO auctionDAO, BidTransactionDAO bidTransactionDAO,
            UserDAO userDAO) {
        this.auctionManager = auctionManager;
        this.auctionDAO = auctionDAO;
        this.bidTransactionDAO = bidTransactionDAO;
        this.userDAO = userDAO;
        this.autoBidManager = AutoBidManager.getInstance();
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
        if (auction == null) throw new IllegalArgumentException("Không tìm thấy phiên đấu giá");
        if (bidder == null) throw new IllegalArgumentException("Không tìm thấy Bidder");

        ReentrantLock bidLock = AUCTION_LOCKS.computeIfAbsent(auction.getAuctionId(), id -> new ReentrantLock());
        bidLock.lock();
        try {
            validateBid(auction, bidder, amount);

            if (bidder.getWallet() == null) throw new InvalidBidException("Không đủ số dư");
            if (!bidder.getWallet().withdraw(amount)) throw new InvalidBidException("Không đủ số dư");
            if (!persistBidder(bidder)) {
                bidder.getWallet().deposit(amount);
                throw new InvalidBidException("Không cập nhật được số dư");
            }

            BidTransaction bid = new BidTransaction(0, auction.getAuctionId(), bidder.getId(), amount);
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

                // ANTI-SNIPING
                boolean extended = auction.checkAndExtendForSniping(bid.getBidTime());
                if (extended) auctionDAO.update(auction);

                // AUTO-BID: chạy async trên thread riêng
                triggerAutoBidsAsync(auction, bid);

                return bid;
            } catch (RuntimeException e) {
                rollbackAuctionState(auction, bid);
                bidder.getWallet().deposit(amount);
                persistBidder(bidder);
                try { auctionDAO.update(auction); } catch (RuntimeException ignored) {}
                throw e;
            }
        } finally {
            bidLock.unlock();
        }
    }

    private void triggerAutoBidsAsync(Auction auction, BidTransaction triggeredBid) {
        AUTO_BID_EXECUTOR.submit(() -> {
            try {
                autoBidManager.processAutoBids(auction, triggeredBid,
                        new BidTransactionDAO(), new UserDAO(), new AuctionDAO());
            } catch (Exception e) {
                System.err.println("AutoBid (async): " + e.getMessage());
            }
        });
    }

    public List<BidTransaction> getAuctionBidHistory(String auctionId) {
        int id = Integer.parseInt(auctionId);
        return bidTransactionDAO.findByAuctionId(id);
    }

    public List<BidTransaction> getBidderBidHistory(String bidderId) {
        int id = Integer.parseInt(bidderId);
        return bidTransactionDAO.findByBidderId(id);
    }

    public BidTransaction getCurrentHighestBid(String auctionId) {
        int id;
        try {
            id = Integer.parseInt(auctionId);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("ID không hợp lệ: " + auctionId);
        }
        return bidTransactionDAO.findHighestBidByAuctionId(id);
    }

    private void validateBid(Auction auction, Bidder bidder, double amount) {
        if (amount <= 0) throw new InvalidBidException("Giá không hợp lệ");
        if (auction.getItem() == null) throw new InvalidBidException("Phiên đấu giá không hợp lệ");
        if (isSellerBiddingOwnAuction(auction, bidder)) throw new InvalidBidException("Không thể tự đấu giá sản phẩm của chính mình");
        if (auction.getStatus() != AuctionStatus.RUNNING && auction.getStatus() != AuctionStatus.OPEN) throw new AuctionClosedException("Phiên đấu giá đã đóng");
        if (auction.isClosed()) throw new AuctionClosedException("Phiên đấu giá đã đóng");
        if (amount < auction.getItem().getStartingPrice()) throw new InvalidBidException("Giá đặt phải lớn hơn giá ban đầu");
        if (auction.getCurrentHighestBid() > 0 && amount <= auction.getCurrentHighestBid()) throw new InvalidBidException("Giá đặt phải lớn hơn giá cao nhất");
        if (bidder.getWallet() == null || bidder.getWallet().getBalance() < amount) throw new InvalidBidException("Không đủ số dư");
    }

    private boolean isSellerBiddingOwnAuction(Auction auction, Bidder bidder) {
        return auction.getSellerId() != null && bidder != null && auction.getSellerId().equals(String.valueOf(bidder.getId()));
    }

    private boolean persistBidder(Bidder bidder) {
        try { userDAO.update(bidder); return true; } catch (RuntimeException e) { return false; }
    }

    private void rollbackAuctionState(Auction auction, BidTransaction bid) {
        auction.getBidHistory().remove(bid);
        if (!auction.getBidHistory().isEmpty()) {
            BidTransaction last = auction.getBidHistory().get(auction.getBidHistory().size() - 1);
            auction.setCurrentHighestBid(last.getBidAmount());
            auction.setCurrentLeaderId(last.getBidderId());
        } else {
            auction.setCurrentHighestBid(auction.getItem() != null ? auction.getItem().getStartingPrice() : 0);
            auction.setCurrentLeaderId(null);
        }
    }
}
