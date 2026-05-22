package server.manager;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public final class AuctionLockManager {
    private static final ConcurrentHashMap<Integer, ReentrantLock> AUCTION_LOCKS = new ConcurrentHashMap<>();

    private AuctionLockManager() {
    }

    public static ReentrantLock getLock(int auctionId) {
        return AUCTION_LOCKS.computeIfAbsent(auctionId, id -> new ReentrantLock());
    }
}
