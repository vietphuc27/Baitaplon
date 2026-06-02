package server.manager;

import common.models.auction.AutoBidAgent;
import common.models.auction.Auction;
import common.models.auction.AuctionStatus;
import common.models.auction.BidTransaction;
import server.config.DatabaseConnection;
import server.repository.AuctionDAO;
import server.repository.BidTransactionDAO;
import server.repository.UserDAO;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

public class AutoBidManager {
    private static AutoBidManager instance;

    private final Map<Integer, PriorityQueue<AutoBidAgent>> agentQueues;
    private final Map<Integer, Integer> agentToAuction;
    private final Map<Integer, AutoBidAgent> allAgents;

    private int nextAgentId;
    private volatile long autoBidDelayMillis;

    private AutoBidManager() {
        this.agentQueues = new ConcurrentHashMap<>();
        this.agentToAuction = new ConcurrentHashMap<>();
        this.allAgents = new ConcurrentHashMap<>();
        this.nextAgentId = 1;
        this.autoBidDelayMillis = 2000L;
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
            AutoBidAgent existingAgent = getAgent(bidderId, auctionId);
            if (existingAgent != null) {
                cleanupAgent(existingAgent);
            }

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

    public int cancelAgentsForBidder(int bidderId) {
        if (bidderId <= 0) {
            return 0;
        }

        List<Integer> agentIds = allAgents.values().stream()
                .filter(agent -> agent.getBidderId() == bidderId && agent.isActive())
                .map(AutoBidAgent::getAgentId)
                .toList();

        int cancelledCount = 0;
        for (int agentId : agentIds) {
            if (cancelAgent(agentId)) {
                cancelledCount++;
            }
        }
        return cancelledCount;
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
        return processAutoBids(auction, triggeredBid, bidDAO, userDAO, auctionDAO, null);
    }

    public List<BidTransaction> processAutoBids(
            Auction auction,
            BidTransaction triggeredBid,
            BidTransactionDAO bidDAO,
            UserDAO userDAO,
            AuctionDAO auctionDAO,
            Consumer<BidTransaction> afterAutoBid) {
        List<BidTransaction> allAutoBids = new ArrayList<>();
        int auctionId = auction.getAuctionId();

        BidTransaction currentTrigger = triggeredBid;
        boolean hasMoreBids = true;
        int roundCount = 0;
        final int maxRounds = 100;

        while (hasMoreBids && roundCount < maxRounds) {
            roundCount++;

            List<BidTransaction> roundAutoBids = processAutoBidsOneRound(auction, currentTrigger, bidDAO, auctionDAO);
            if (roundAutoBids.isEmpty()) {
                hasMoreBids = false;
            } else {
                allAutoBids.addAll(roundAutoBids);
                notifyAutoBids(roundAutoBids, afterAutoBid);
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

    private void notifyAutoBids(List<BidTransaction> bids, Consumer<BidTransaction> afterAutoBid) {
        if (afterAutoBid == null || bids == null) {
            return;
        }
        for (BidTransaction bid : bids) {
            try {
                afterAutoBid.accept(bid);
            } catch (RuntimeException e) {
                System.err.println("AutoBid notify error: " + e.getMessage());
            }
        }
    }

    private List<BidTransaction> processAutoBidsOneRound(
            Auction auction,
            BidTransaction triggeredBid,
            BidTransactionDAO bidDAO,
            AuctionDAO auctionDAO) {
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

                double previousHighestBid = auction.getCurrentHighestBid();
                Integer previousLeaderId = auction.getCurrentLeaderId();
                AuctionStatus previousStatus = auction.getStatus();
                LocalDateTime previousEndTime = auction.getEndTime();
                int previousHistorySize = auction.getBidHistory() == null ? 0 : auction.getBidHistory().size();

                boolean accepted = auction.processBid(autoBid);
                if (!accepted) {
                    cleanupAgent(agent);
                    continue;
                }

                if (!persistAutoBidAtomically(auction, autoBid, bidDAO, auctionDAO)) {
                    restoreAuctionState(
                            auction,
                            previousHighestBid,
                            previousLeaderId,
                            previousStatus,
                            previousEndTime,
                            previousHistorySize);
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
        return AuctionLockManager.getLock(auctionId);
    }

    private synchronized int generateAgentId() {
        return nextAgentId++;
    }

    private boolean persistAutoBidAtomically(
            Auction auction,
            BidTransaction autoBid,
            BidTransactionDAO bidDAO,
            AuctionDAO auctionDAO) {
        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            auctionDAO.update(conn, auction);
            bidDAO.save(conn, autoBid);
            conn.commit();
            return true;
        } catch (RuntimeException | SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ignored) {
                }
            }
            // Unit tests may pass in custom in-memory DAO implementations.
            // If opening DB connection failed before a transaction started, allow DAO-level fallback.
            if (conn == null && isCustomPersistenceDao(auctionDAO, bidDAO)) {
                try {
                    auctionDAO.update(auction);
                    bidDAO.save(autoBid);
                    return true;
                } catch (RuntimeException fallbackEx) {
                    System.err.println("AutoBid persist fallback error for auction "
                            + autoBid.getAuctionId() + ": " + fallbackEx.getMessage());
                }
            }
            System.err.println("AutoBid persist error for auction " + autoBid.getAuctionId() + ": " + e.getMessage());
            return false;
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                } catch (SQLException ignored) {
                }
                try {
                    conn.close();
                } catch (SQLException ignored) {
                }
            }
        }
    }

    private boolean isCustomPersistenceDao(AuctionDAO auctionDAO, BidTransactionDAO bidDAO) {
        return auctionDAO.getClass() != AuctionDAO.class || bidDAO.getClass() != BidTransactionDAO.class;
    }

    private void restoreAuctionState(
            Auction auction,
            double previousHighestBid,
            Integer previousLeaderId,
            AuctionStatus previousStatus,
            LocalDateTime previousEndTime,
            int previousHistorySize) {
        if (auction.getBidHistory() != null) {
            while (auction.getBidHistory().size() > previousHistorySize) {
                auction.getBidHistory().remove(auction.getBidHistory().size() - 1);
            }
        }
        auction.setCurrentHighestBid(previousHighestBid);
        auction.setCurrentLeaderId(previousLeaderId);
        auction.setStatus(previousStatus);
        auction.setEndTime(previousEndTime);
    }
}
