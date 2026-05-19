package server.service;

import common.exceptions.AuctionClosedException;
import common.exceptions.InvalidBidException;
import common.models.auction.Auction;
import common.models.auction.AuctionStatus;
import common.models.auction.AutoBidAgent;
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
    private static final ConcurrentHashMap<Integer, ReentrantLock> BIDDER_LOCKS = new ConcurrentHashMap<>();
    private static final double EPSILON = 1e-9;

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

    public BidService(
            AuctionManager auctionManager,
            AuctionDAO auctionDAO,
            BidTransactionDAO bidTransactionDAO,
            UserDAO userDAO
    ) {
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
            throw new IllegalArgumentException("ID khong hop le: " + auctionId);
        }

        Auction auction = auctionManager.getAuctionById(auctionIdInt);
        if (auction == null) {
            auction = auctionDAO.findById(auctionIdInt)
                    .orElseThrow(() -> new IllegalArgumentException("Khong tim thay phien dau gia: " + auctionId));
            auctionManager.addAuction(auction);
        }

        return placeBid(auction, bidder, amount);
    }

    public BidTransaction placeBid(Auction auction, Bidder bidder, double amount) {
        if (auction == null) throw new IllegalArgumentException("Khong tim thay phien dau gia");
        if (bidder == null) throw new IllegalArgumentException("Khong tim thay bidder");

        ReentrantLock bidderLock = BIDDER_LOCKS.computeIfAbsent(bidder.getId(), id -> new ReentrantLock());
        ReentrantLock auctionLock = AUCTION_LOCKS.computeIfAbsent(auction.getAuctionId(), id -> new ReentrantLock());

        bidderLock.lock();
        auctionLock.lock();
        try {
            validateBid(auction, bidder, amount);

            BidTransaction bid = new BidTransaction(0, auction.getAuctionId(), bidder.getId(), amount);
            bid.setBidTime(LocalDateTime.now());
            bid.setBidder(bidder);

            boolean accepted = auction.processBid(bid);
            if (!accepted) {
                throw new InvalidBidException("Gia dat khong hop le hoac phien da dong");
            }

            try {
                auctionDAO.update(auction);
                bidTransactionDAO.save(bid);

                boolean extended = auction.checkAndExtendForSniping(bid.getBidTime());
                if (extended) {
                    auctionDAO.update(auction);
                }

                triggerAutoBidsAsync(auction, bid);
                return bid;
            } catch (RuntimeException e) {
                rollbackAuctionState(auction, bid);
                try {
                    auctionDAO.update(auction);
                } catch (RuntimeException ignored) {
                }
                throw e;
            }
        } finally {
            auctionLock.unlock();
            bidderLock.unlock();
        }
    }

    public int registerAutoBid(int bidderId, int auctionId, double maxBid, double increment) {
        ReentrantLock bidderLock = BIDDER_LOCKS.computeIfAbsent(bidderId, id -> new ReentrantLock());
        bidderLock.lock();
        try {
            User user = userDAO.findById(bidderId)
                    .orElseThrow(() -> new IllegalArgumentException("Khong tim thay bidder"));
            if (!(user instanceof Bidder bidder)) {
                throw new IllegalArgumentException("User khong phai bidder");
            }

            Auction auction = auctionManager.getAuctionById(auctionId);
            if (auction == null) {
                auction = auctionDAO.findById(auctionId)
                        .orElseThrow(() -> new IllegalArgumentException("Khong tim thay phien dau gia: " + auctionId));
                auctionManager.addAuction(auction);
            }

            validateAutoBidRegistration(auction, bidder, maxBid, increment);

            int agentId = autoBidManager.registerAgent(bidderId, auctionId, maxBid, increment);
            if (agentId <= 0) {
                throw new InvalidBidException("Thong so auto-bid khong hop le");
            }
            return agentId;
        } finally {
            bidderLock.unlock();
        }
    }

    private void triggerAutoBidsAsync(Auction auction, BidTransaction triggeredBid) {
        AUTO_BID_EXECUTOR.submit(() -> {
            try {
                autoBidManager.processAutoBids(
                        auction,
                        triggeredBid,
                        new BidTransactionDAO(),
                        new UserDAO(),
                        new AuctionDAO());
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
            throw new IllegalArgumentException("ID khong hop le: " + auctionId);
        }
        return bidTransactionDAO.findHighestBidByAuctionId(id);
    }

    private void validateBid(Auction auction, Bidder bidder, double amount) {
        if (amount <= 0) throw new InvalidBidException("Gia khong hop le");

        if (auction.getItem() == null) throw new InvalidBidException("Phien dau gia khong hop le");
        
        if (isSellerBiddingOwnAuction(auction, bidder)) {
            throw new InvalidBidException("Khong the tu dau gia san pham cua chinh minh");
        }
        if (auction.getStatus() != AuctionStatus.RUNNING && auction.getStatus() != AuctionStatus.OPEN) {
            throw new AuctionClosedException("Phien dau gia da dong");
        }
        if (auction.isClosed()) throw new AuctionClosedException("Phien dau gia da dong");
        if (amount < auction.getItem().getStartingPrice()) {
            throw new InvalidBidException("Gia dat phai lon hon gia ban dau");
        }
        if (auction.getCurrentHighestBid() > 0 && amount <= auction.getCurrentHighestBid()) {
            throw new InvalidBidException("Gia dat phai lon hon gia cao nhat");
        }
        if (bidder.getWallet() == null) throw new InvalidBidException("Khong du so du");

        double available = calculateAvailableForAuction(bidder, auction.getAuctionId());
        if (amount > available + EPSILON) {
            throw new InvalidBidException("Khong du so du kha dung");
        }
    }

    private void validateAutoBidRegistration(Auction auction, Bidder bidder, double maxBid, double increment) {
        if (maxBid <= 0 || increment <= 0) {
            throw new InvalidBidException("Thong so auto-bid khong hop le");
        }
        if (auction.getItem() == null) throw new InvalidBidException("Phien dau gia khong hop le");
        if (isSellerBiddingOwnAuction(auction, bidder)) {
            throw new InvalidBidException("Khong the tu dau gia san pham cua chinh minh");
        }
        if (auction.getStatus() != AuctionStatus.RUNNING && auction.getStatus() != AuctionStatus.OPEN) {
            throw new AuctionClosedException("Phien dau gia da dong");
        }
        if (auction.isClosed()) throw new AuctionClosedException("Phien dau gia da dong");
        if (maxBid <= auction.getCurrentHighestBid()) {
            throw new InvalidBidException("Gia tran phai lon hon gia hien tai");
        }
        if (bidder.getWallet() == null) throw new InvalidBidException("Khong du so du");

        double available = calculateAvailableForAuction(bidder, auction.getAuctionId());
        if (maxBid > available + EPSILON) {
            throw new InvalidBidException("Khong du so du kha dung");
        }
    }

    private double calculateAvailableForAuction(Bidder bidder, int currentAuctionId) {
        double walletBalance = bidder.getWallet() == null ? 0.0 : bidder.getWallet().getBalance();
        double locked = calculateLockedAmountExcludingAuction(bidder.getId(), currentAuctionId);
        return Math.max(0.0, walletBalance - locked);
    }

    private double calculateLockedAmountExcludingAuction(int bidderId, int excludedAuctionId) {
        double lockedAmount = 0.0;
        for (Auction activeAuction : auctionDAO.findAll()) {
            if (activeAuction == null || activeAuction.isClosed()) {
                continue;
            }
            if (activeAuction.getAuctionId() == excludedAuctionId) {
                continue;
            }

            AutoBidAgent agent = autoBidManager.getAgent(bidderId, activeAuction.getAuctionId());
            if (agent != null) {
                lockedAmount += Math.max(0.0, agent.getMaxBid());
                continue;
            }

            Integer leaderId = activeAuction.getCurrentLeaderId();
            if (leaderId != null && leaderId == bidderId) {
                lockedAmount += Math.max(0.0, activeAuction.getCurrentHighestBid());
            }
        }
        return lockedAmount;
    }

    private boolean isSellerBiddingOwnAuction(Auction auction, Bidder bidder) {
        return auction.getSellerId() != null
                && bidder != null
                && auction.getSellerId().equals(String.valueOf(bidder.getId()));
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
