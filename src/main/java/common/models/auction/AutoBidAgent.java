package common.models.auction;

import java.time.LocalDateTime;

/**
 * Đại diện cho một Agent tự động đặt giá.
 * Implement Comparable để dùng với PriorityQueue:
 * - Ưu tiên maxBid cao nhất
 * - Nếu maxBid bằng nhau, ưu tiên agent được tạo sớm hơn (FIFO)
 */
public class AutoBidAgent implements Comparable<AutoBidAgent> {
    private int agentId;
    private int bidderId;
    private int auctionId;
    private double maxBid; // Giá trần tối đa
    private double increment; // Bước giá mỗi lần tăng
    private volatile boolean active; // true nếu agent đang hoạt động
    private LocalDateTime createdAt;

    public AutoBidAgent() {
    }

    public AutoBidAgent(int agentId, int bidderId, int auctionId, double maxBid, double increment) {
        this.agentId = agentId;
        this.bidderId = bidderId;
        this.auctionId = auctionId;
        this.maxBid = maxBid;
        this.increment = increment;
        this.active = true;
        this.createdAt = LocalDateTime.now();
    }

    /**
     * Tính toán giá đề xuất dựa trên giá hiện tại.
     * 
     * @param currentHighestBid Giá cao nhất hiện tại
     * @return Giá đề xuất = currentHighestBid + increment (nếu không vượt maxBid),
     *         ngược lại trả về -1
     */
    public double calculateProposedBid(double currentHighestBid) {
        double proposed = currentHighestBid + increment;
        if (proposed > maxBid) {
            return -1; // Vượt quá giá trần
        }
        return proposed;
    }

    /**
     * Kiểm tra xem agent có đủ điều kiện để bid không.
     */
    public boolean canBid(double currentHighestBid) {
        return active && (currentHighestBid + increment) <= maxBid;
    }

    @Override
    public int compareTo(AutoBidAgent other) {
        // Ưu tiên maxBid cao nhất (giảm dần)
        int cmp = Double.compare(other.maxBid, this.maxBid);
        if (cmp != 0) {
            return cmp;
        }
        // Nếu maxBid bằng nhau, ưu tiên agent cũ hơn (FIFO)
        if (this.createdAt != null && other.createdAt != null) {
            return this.createdAt.compareTo(other.createdAt);
        }
        return 0;
    }

    @Override
    public String toString() {
        return "AutoBidAgent{" +
                "agentId=" + agentId +
                ", bidderId=" + bidderId +
                ", maxBid=" + maxBid +
                ", increment=" + increment +
                ", active=" + active +
                '}';
    }

    // ─── GETTERS & SETTERS ───

    public int getAgentId() {
        return agentId;
    }

    public void setAgentId(int agentId) {
        this.agentId = agentId;
    }

    public int getBidderId() {
        return bidderId;
    }

    public void setBidderId(int bidderId) {
        this.bidderId = bidderId;
    }

    public int getAuctionId() {
        return auctionId;
    }

    public void setAuctionId(int auctionId) {
        this.auctionId = auctionId;
    }

    public double getMaxBid() {
        return maxBid;
    }

    public void setMaxBid(double maxBid) {
        this.maxBid = maxBid;
    }

    public double getIncrement() {
        return increment;
    }

    public void setIncrement(double increment) {
        this.increment = increment;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
