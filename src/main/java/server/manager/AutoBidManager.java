package server.manager;

import common.models.auction.AutoBidAgent;
import common.models.auction.Auction;
import common.models.auction.BidTransaction;
import common.models.user.Bidder;
import common.models.user.User;
import server.repository.AuctionDAO;
import server.repository.BidTransactionDAO;
import server.repository.UserDAO;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public class AutoBidManager {
    private static AutoBidManager instance;

    private final Map<Integer, PriorityQueue<AutoBidAgent>> agentQueues;
    private final Map<Integer, Integer> agentToAuction;
    private final Map<Integer, ReentrantLock> auctionLocks;
    private final Map<Integer, AutoBidAgent> allAgents;

    private int nextAgentId;
    private volatile long autoBidDelayMillis;

    private AutoBidManager() {
        this.agentQueues = new ConcurrentHashMap<>();
        this.agentToAuction = new ConcurrentHashMap<>();
        this.auctionLocks = new ConcurrentHashMap<>();
        this.allAgents = new ConcurrentHashMap<>();
        this.nextAgentId = 1;
        this.autoBidDelayMillis = 450L;
    }

    public static AutoBidManager getInstance() {
        if (instance == null) {
            synchronized (AutoBidManager.class) {
                if (instance == null) {
                    instance = new AutoBidManager();
                }
            }
        }
        return instance;
    }

    public int registerAgent(int bidderId, int auctionId, double maxBid, double increment) {
        if (maxBid <= 0 || increment <= 0) {
            return -1;
        }

        ReentrantLock lock = getLock(auctionId);
        lock.lock();
        try {
            int agentId = generateAgentId();
            AutoBidAgent agent = new AutoBidAgent(agentId, bidderId, auctionId, maxBid, increment);
            PriorityQueue<AutoBidAgent> queue = agentQueues.computeIfAbsent(auctionId, id -> new PriorityQueue<>());
            queue.offer(agent);
            allAgents.put(agentId, agent);
            agentToAuction.put(agentId, auctionId);
            System.out.println("AutoBid: registered agent " + agentId + " for bidder " + bidderId + " in auction " + auctionId);
            return agentId;
        } finally {
            lock.unlock();
        }
    }

    public boolean cancelAgent(int agentId) {
        AutoBidAgent agent = allAgents.remove(agentId);
        if (agent == null) {
            return false;
        }

        agent.setActive(false);
        agentToAuction.remove(agentId);
        System.out.println("AutoBid: canceled agent " + agentId + " for auction " + agent.getAuctionId());
        return true;
    }

    public List<BidTransaction> processAutoBids(Auction auction, BidTransaction triggeredBid) {
        return processAutoBids(auction, triggeredBid, new BidTransactionDAO(), new UserDAO(), new AuctionDAO());
    }

    public List<BidTransaction> processAutoBids(
            Auction auction,
            BidTransaction triggeredBid,
            BidTransactionDAO bidDAO,
            UserDAO userDAO,
            AuctionDAO auctionDAO) {
        List<BidTransaction> allAutoBids = new ArrayList<>();
        int auctionId = auction.getAuctionId();

        BidTransaction currentTrigger = triggeredBid;
        boolean hasMoreBids = true;
        int roundCount = 0;
        final int maxRounds = 100;

        while (hasMoreBids && roundCount < maxRounds) {
            roundCount++;

            List<BidTransaction> roundAutoBids = processAutoBidsOneRound(auction, currentTrigger);
            if (roundAutoBids.isEmpty()) {
                hasMoreBids = false;
            } else {
                for (BidTransaction autoBid : roundAutoBids) {
                    try {
                        bidDAO.save(autoBid);
                        User autoBidder = userDAO.findById(autoBid.getBidderId()).orElse(null);
                        if (autoBidder instanceof Bidder bidder && bidder.getWallet() != null) {
                            bidder.getWallet().withdraw(autoBid.getBidAmount());
                            userDAO.update(bidder);
                        }
                    } catch (RuntimeException e) {
                        System.err.println("AutoBid persist error for auction " + autoBid.getAuctionId() + ": " + e.getMessage());
                    }
                }

                try {
                    auctionDAO.update(auction);
                } catch (RuntimeException e) {
                    System.err.println("AutoBid update auction error: " + e.getMessage());
                }

                allAutoBids.addAll(roundAutoBids);
                currentTrigger = roundAutoBids.get(roundAutoBids.size() - 1);
            }

            if (!hasAnyActiveAgentForAuction(auctionId)) {
                break;
            }

            if (hasMoreBids && autoBidDelayMillis > 0) {
                try {
                    Thread.sleep(autoBidDelayMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        return allAutoBids;
    }

    private List<BidTransaction> processAutoBidsOneRound(Auction auction, BidTransaction triggeredBid) {
        List<BidTransaction> autoBids = new ArrayList<>();
        int auctionId = auction.getAuctionId();

        ReentrantLock lock = getLock(auctionId);
        lock.lock();
        try {
            PriorityQueue<AutoBidAgent> queue = agentQueues.get(auctionId);
            if (queue == null || queue.isEmpty()) {
                return autoBids;
            }

            Integer currentLeaderId = auction.getCurrentLeaderId();
            if (currentLeaderId == null) {
                currentLeaderId = triggeredBid.getBidderId();
            }

            PriorityQueue<AutoBidAgent> remaining = new PriorityQueue<>();

            while (!queue.isEmpty()) {
                AutoBidAgent agent = queue.poll();
                if (!isAgentStillActive(agent)) {
                    cleanupAgent(agent);
                    continue;
                }
                if (agent.getBidderId() == currentLeaderId) {
                    remaining.offer(agent);
                    continue;
                }

                double currentHighest = auction.getCurrentHighestBid();
                if (!agent.canBid(currentHighest)) {
                    cleanupAgent(agent);
                    continue;
                }

                double proposedBid = agent.calculateProposedBid(currentHighest);
                if (proposedBid <= 0 || !isAgentStillActive(agent)) {
                    cleanupAgent(agent);
                    continue;
                }

                BidTransaction autoBid = new BidTransaction(0, auctionId, agent.getBidderId(), proposedBid);
                autoBid.setBidTime(LocalDateTime.now());

                boolean accepted = auction.processBid(autoBid);
                if (!accepted) {
                    cleanupAgent(agent);
                    continue;
                }

                autoBids.add(autoBid);
                if (agent.canBid(auction.getCurrentHighestBid()) && isAgentStillActive(agent)) {
                    remaining.offer(agent);
                } else {
                    cleanupAgent(agent);
                }
            }

            agentQueues.put(auctionId, remaining);
        } finally {
            lock.unlock();
        }

        return autoBids;
    }

    public boolean hasActiveAgent(int bidderId, int auctionId) {
        return allAgents.values().stream()
                .anyMatch(a -> a.getBidderId() == bidderId && a.getAuctionId() == auctionId && a.isActive());
    }

    public AutoBidAgent getAgent(int bidderId, int auctionId) {
        return allAgents.values().stream()
                .filter(a -> a.getBidderId() == bidderId && a.getAuctionId() == auctionId && a.isActive())
                .findFirst()
                .orElse(null);
    }

    public void resetForTesting() {
        agentQueues.clear();
        agentToAuction.clear();
        auctionLocks.clear();
        allAgents.clear();
        nextAgentId = 1;
        autoBidDelayMillis = 0L;
    }

    public void setAutoBidDelayMillis(long autoBidDelayMillis) {
        this.autoBidDelayMillis = Math.max(0L, autoBidDelayMillis);
    }

    private boolean hasAnyActiveAgentForAuction(int auctionId) {
        return allAgents.values().stream()
                .anyMatch(a -> a.getAuctionId() == auctionId && a.isActive());
    }

    private boolean isAgentStillActive(AutoBidAgent agent) {
        if (agent == null || !agent.isActive()) {
            return false;
        }
        AutoBidAgent mapped = allAgents.get(agent.getAgentId());
        return mapped == agent && mapped.isActive();
    }

    private void cleanupAgent(AutoBidAgent agent) {
        if (agent == null) {
            return;
        }
        agent.setActive(false);
        allAgents.remove(agent.getAgentId());
        agentToAuction.remove(agent.getAgentId());
    }

    private ReentrantLock getLock(int auctionId) {
        return auctionLocks.computeIfAbsent(auctionId, id -> new ReentrantLock());
    }

    private synchronized int generateAgentId() {
        return nextAgentId++;
    }
}
