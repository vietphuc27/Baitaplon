package server.manager;

import common.models.auction.Auction;
import common.models.auction.AuctionStatus;
import common.models.item.Art;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ManagerCoverageTest {

    @AfterEach
    public void tearDown() throws Exception {
        resetAuctionManagerSingleton();
        SessionManager.getInstance().logout();
    }

    @Test
    public void auctionManagerCoversNullDuplicateLookupAndRunningFilter() throws Exception {
        resetAuctionManagerSingleton();
        AuctionManager manager = AuctionManager.getInstance();

        manager.addAuction(null);
        assertTrue(manager.getAllActiveAuctions().isEmpty());

        Auction open = new Auction(1, new Art(10, "a", "d", 1, "s", "ar"), "s",
                LocalDateTime.now().minusHours(1), LocalDateTime.now().plusHours(1));
        open.setStatus(AuctionStatus.OPEN);

        Auction running = new Auction(2, new Art(11, "b", "d", 1, "s", "ar"), "s",
                LocalDateTime.now().minusHours(1), LocalDateTime.now().plusHours(1));
        running.setStatus(AuctionStatus.RUNNING);

        manager.addAuction(open);
        manager.addAuction(running);

        Auction replacement = new Auction(2, new Art(12, "c", "d", 1, "s", "ar"), "s",
                LocalDateTime.now().minusHours(1), LocalDateTime.now().plusHours(1));
        replacement.setStatus(AuctionStatus.RUNNING);
        manager.addAuction(replacement);

        assertEquals(2, manager.getAllActiveAuctions().size());
        assertSame(replacement, manager.getAuctionById(2));
        assertNull(manager.getAuctionById(999));

        List<Auction> runningList = manager.getRunningAuctions();
        assertEquals(1, runningList.size());
        assertEquals(2, runningList.getFirst().getAuctionId());
    }

    @Test
    public void sessionManagerLogoutWhenNoCurrentUserIsSafe() {
        SessionManager manager = SessionManager.getInstance();
        manager.logout();
        assertFalse(manager.isUserLoggedIn());
        assertNull(manager.getCurrentUser());
    }

    private void resetAuctionManagerSingleton() throws Exception {
        Field instanceField = AuctionManager.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, null);
    }
}
