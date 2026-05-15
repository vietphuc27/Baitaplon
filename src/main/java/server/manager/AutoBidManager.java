package server.manager;

import common.models.auction.AutoBidAgent;
import common.models.auction.Auction;
import common.models.auction.BidTransaction;
import common.models.user.Bidder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Quản lý các Agent tự động đặt giá (Auto-Bidding).
 * - Singleton Pattern
 * - Mỗi Auction có một PriorityQueue riêng để quản lý các agent
 * - Thread-safe với ReentrantLock trên mỗi auction
 * - PriorityQueue sắp xếp agent theo maxBid giảm dần (cao nhất ưu tiên trước)
 */
public class AutoBidManager {
    private static AutoBidManager instance;

    // Map<AuctionId, PriorityQueue<AutoBidAgent>> — mỗi auction có queue riêng
    private final Map<Integer, PriorityQueue<AutoBidAgent>> agentQueues;
    // Map<AgentId, AuctionId> — tra ngược để biết agent thuộc auction nào
    private final Map<Integer, Integer> agentToAuction;
    // Map<AuctionId, ReentrantLock> — lock riêng cho từng auction
    private final Map<Integer, ReentrantLock> auctionLocks;
    // Map<AgentId, AutoBidAgent> — lưu toàn bộ agent đang active
    private final Map<Integer, AutoBidAgent> allAgents;

    private int nextAgentId;

    private AutoBidManager() {
        this.agentQueues = new ConcurrentHashMap<>();
        this.agentToAuction = new ConcurrentHashMap<>();
        this.auctionLocks = new ConcurrentHashMap<>();
        this.allAgents = new ConcurrentHashMap<>();
        this.nextAgentId = 1;
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

    /**
     * Đăng ký một Agent tự động đặt giá.
     *
     * @param bidderId  ID người dùng
     * @param auctionId ID phiên đấu giá
     * @param maxBid    Giá trần tối đa
     * @param increment Bước giá mỗi lần tăng
     * @return ID của agent vừa tạo, hoặc -1 nếu thất bại
     */
    public int registerAgent(int bidderId, int auctionId, double maxBid, double increment) {
        if (maxBid <= 0 || increment <= 0) {
            return -1;
        }

        ReentrantLock lock = getLock(auctionId);
        lock.lock();
        try {
            // Tạo agent mới
            int agentId = generateAgentId();
            AutoBidAgent agent = new AutoBidAgent(agentId, bidderId, auctionId, maxBid, increment);

            // Thêm vào PriorityQueue của auction
            PriorityQueue<AutoBidAgent> queue = agentQueues.computeIfAbsent(
                    auctionId, k -> new PriorityQueue<>());
            queue.offer(agent);

            // Lưu mapping
            agentToAuction.put(agentId, auctionId);
            allAgents.put(agentId, agent);

            System.out.println("AutoBid: Đã đăng ký agent " + agentId
                    + " cho bidder " + bidderId
                    + " | auction " + auctionId
                    + " | maxBid=" + maxBid
                    + " | increment=" + increment);

            return agentId;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Hủy đăng ký một Agent.
     *
     * @param agentId ID của agent cần hủy
     * @return true nếu hủy thành công
     */
    public boolean cancelAgent(int agentId) {
        Integer auctionId = agentToAuction.get(agentId);
        if (auctionId == null) {
            return false;
        }

        ReentrantLock lock = getLock(auctionId);
        lock.lock();
        try {
            AutoBidAgent agent = allAgents.get(agentId);
            if (agent != null) {
                agent.setActive(false);
                allAgents.remove(agentId);
                agentToAuction.remove(agentId);

                System.out.println("AutoBid: Đã hủy agent " + agentId);
                return true;
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Xử lý tự động đặt giá sau khi có bid mới.
     * Được gọi từ BidService.placeBid().
     *
     * Trả về danh sách BidTransaction của các auto-bid đã được xử lý thành công.
     * BidService sẽ chịu trách nhiệm persist các transaction này + trừ tiền bidder.
     *
     * @param auction      Phiên đấu giá
     * @param triggeredBid Bid vừa được đặt (có thể là của auto-bid hoặc người dùng)
     * @return Danh sách BidTransaction của các auto-bid đã xử lý
     */
    public List<BidTransaction> processAutoBids(Auction auction, BidTransaction triggeredBid) {
        List<BidTransaction> autoBids = new ArrayList<>();
        int auctionId = auction.getAuctionId();
        ReentrantLock lock = getLock(auctionId);
        lock.lock();
        try {
            PriorityQueue<AutoBidAgent> queue = agentQueues.get(auctionId);
            if (queue == null || queue.isEmpty()) {
                return autoBids;
            }

            // Dùng queue tạm để lưu các agent chưa xử lý
            PriorityQueue<AutoBidAgent> remaining = new PriorityQueue<>();

            while (!queue.isEmpty()) {
                AutoBidAgent agent = queue.poll();

                // Bỏ qua agent không active
                if (!agent.isActive()) {
                    continue;
                }

                // Bỏ qua agent của người vừa bid (không tự bid lại chính mình)
                if (agent.getBidderId() == triggeredBid.getBidderId()
                        && triggeredBid.getBidder() != null) {
                    remaining.offer(agent);
                    continue;
                }

                // Kiểm tra agent có đủ điều kiện không
                double currentHighest = auction.getCurrentHighestBid();
                if (!agent.canBid(currentHighest)) {
                    agent.setActive(false);
                    allAgents.remove(agent.getAgentId());
                    agentToAuction.remove(agent.getAgentId());
                    continue;
                }

                // Tính giá đề xuất
                double proposedBid = agent.calculateProposedBid(currentHighest);
                if (proposedBid <= 0) {
                    agent.setActive(false);
                    allAgents.remove(agent.getAgentId());
                    agentToAuction.remove(agent.getAgentId());
                    continue;
                }

                // Tạo BidTransaction cho auto-bid
                BidTransaction autoBid = new BidTransaction(
                        0,
                        auctionId,
                        agent.getBidderId(),
                        proposedBid);
                autoBid.setBidTime(LocalDateTime.now());

                // Process bid vào auction (chỉ cập nhật currentHighestBid trong RAM)
                boolean accepted = auction.processBid(autoBid);
                if (accepted) {
                    autoBids.add(autoBid);

                    // Agent vẫn còn trong queue nếu vẫn còn khả năng trả giá
                    if (agent.canBid(auction.getCurrentHighestBid())) {
                        remaining.offer(agent);
                    } else {
                        agent.setActive(false);
                        allAgents.remove(agent.getAgentId());
                        agentToAuction.remove(agent.getAgentId());
                    }
                } else {
                    // Bid thất bại, agent bị loại
                    agent.setActive(false);
                    allAgents.remove(agent.getAgentId());
                    agentToAuction.remove(agent.getAgentId());
                }
            }

            // Đưa các agent còn lại vào queue
            agentQueues.put(auctionId, remaining);

            if (!autoBids.isEmpty()) {
                System.out.println("AutoBid: Xử lý " + autoBids.size()
                        + " auto-bid cho auction " + auctionId
                        + ", giá mới nhất: " + auction.getCurrentHighestBid());
            }

        } finally {
            lock.unlock();
        }
        return autoBids;
    }

    /**
     * Kiểm tra xem bidder có agent đang active cho auction nào không.
     */
    public boolean hasActiveAgent(int bidderId, int auctionId) {
        PriorityQueue<AutoBidAgent> queue = agentQueues.get(auctionId);
        if (queue == null) {
            return false;
        }
        return allAgents.values().stream()
                .anyMatch(a -> a.getBidderId() == bidderId
                        && a.getAuctionId() == auctionId
                        && a.isActive());
    }

    /**
     * Lấy agent của bidder trong auction (nếu có).
     */
    public AutoBidAgent getAgent(int bidderId, int auctionId) {
        return allAgents.values().stream()
                .filter(a -> a.getBidderId() == bidderId
                        && a.getAuctionId() == auctionId
                        && a.isActive())
                .findFirst()
                .orElse(null);
    }

    /**
     * Lấy toàn bộ agent đang active.
     */
    public Map<Integer, AutoBidAgent> getAllActiveAgents() {
        return new ConcurrentHashMap<>(allAgents);
    }

    private ReentrantLock getLock(int auctionId) {
        return auctionLocks.computeIfAbsent(auctionId, id -> new ReentrantLock());
    }

    private synchronized int generateAgentId() {
        return nextAgentId++;
    }
}