package server.service;

import common.models.auction.Auction;
import common.models.auction.AuctionStatus;
import common.models.item.Electronics;
import common.models.item.Item;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import server.manager.AuctionManager;
import server.repository.AuctionDAO;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AuctionServiceTest {
    private AuctionService auctionService;
    private StubItemService itemService;
    private InMemoryAuctionDAO auctionDAO;
    private AuctionManager auctionManager;

    @BeforeEach
    void setUp() {
        resetAuctionManagerSingleton();
        itemService = new StubItemService();
        auctionService = new AuctionService(itemService);
        auctionDAO = new InMemoryAuctionDAO();
        injectField(auctionService, "auctionDAO", auctionDAO);
        auctionManager = getPrivateAuctionManager(auctionService);
        clearActiveAuctions(auctionManager);
    }

    @AfterEach
    void tearDown() {
        resetAuctionManagerSingleton();
    }

    @Test
    void createAuctionRejectsEndBeforeStart() {
        Item item = new Electronics(100, "Phone", "Desc", 100, "seller-1", 12);
        itemService.put(item);

        LocalDateTime start = LocalDateTime.now().plusMinutes(5);
        LocalDateTime end = LocalDateTime.now().plusMinutes(1);

        assertThrows(
                IllegalArgumentException.class,
                () -> auctionService.createAuction("seller-1", 100, start, end));
    }

    @Test
    void createAuctionRejectsPastStart() {
        Item item = new Electronics(101, "Laptop", "Desc", 200, "seller-1", 12);
        itemService.put(item);

        LocalDateTime start = LocalDateTime.now().minusMinutes(2);
        LocalDateTime end = LocalDateTime.now().plusMinutes(10);

        assertThrows(
                IllegalArgumentException.class,
                () -> auctionService.createAuction("seller-1", 101, start, end));
    }

    @Test
    void createAuctionRejectsMissingItem() {
        LocalDateTime start = LocalDateTime.now().plusMinutes(1);
        LocalDateTime end = LocalDateTime.now().plusMinutes(5);

        assertThrows(
                IllegalArgumentException.class,
                () -> auctionService.createAuction("seller-1", 999, start, end));
    }

    @Test
    void createAuctionRejectsSellerMismatch() {
        Item item = new Electronics(102, "TV", "Desc", 300, "seller-A", 12);
        itemService.put(item);

        LocalDateTime start = LocalDateTime.now().plusMinutes(1);
        LocalDateTime end = LocalDateTime.now().plusMinutes(5);

        assertThrows(
                IllegalArgumentException.class,
                () -> auctionService.createAuction("seller-B", 102, start, end));
    }

    @Test
    void createAuctionSuccessSavesToDaoAndManager() {
        Item item = new Electronics(103, "Camera", "Desc", 400, "seller-1", 12);
        itemService.put(item);

        LocalDateTime start = LocalDateTime.now().plusMinutes(1);
        LocalDateTime end = LocalDateTime.now().plusMinutes(10);

        Auction created = auctionService.createAuction("seller-1", 103, start, end);

        assertNotNull(created);
        assertEquals("seller-1", created.getSellerId());
        assertEquals(AuctionStatus.OPEN, created.getStatus());
        assertTrue(auctionDAO.findById(created.getAuctionId()).isPresent());
        assertNotNull(auctionManager.getAuctionById(created.getAuctionId()));
    }

    @Test
    void getLiveAuctionsReturnsOnlyRunningAuctions() {
        Auction running = testAuction(201, AuctionStatus.RUNNING, LocalDateTime.now().minusMinutes(2), LocalDateTime.now().plusMinutes(2));
        Auction open = testAuction(202, AuctionStatus.OPEN, LocalDateTime.now().plusMinutes(2), LocalDateTime.now().plusMinutes(10));
        Auction finished = testAuction(203, AuctionStatus.FINISHED, LocalDateTime.now().minusMinutes(10), LocalDateTime.now().minusMinutes(1));

        auctionManager.addAuction(running);
        auctionManager.addAuction(open);
        auctionManager.addAuction(finished);

        List<Auction> live = auctionService.getLiveAuctions();

        assertEquals(1, live.size());
        assertEquals(201, live.getFirst().getAuctionId());
    }

    @Test
    void refreshAuctionsStatusMovesOpenToRunning() {
        Auction open = testAuction(301, AuctionStatus.OPEN, LocalDateTime.now().minusMinutes(1), LocalDateTime.now().plusMinutes(5));
        auctionManager.addAuction(open);

        auctionService.refreshAuctionsStatus();

        assertEquals(AuctionStatus.RUNNING, open.getStatus());
        assertTrue(auctionDAO.updatedAuctionIds.contains(301));
    }

    @Test
    void refreshAuctionsStatusMovesRunningToFinished() {
        Auction running = testAuction(302, AuctionStatus.RUNNING, LocalDateTime.now().minusMinutes(5), LocalDateTime.now().minusSeconds(1));
        auctionManager.addAuction(running);

        auctionService.refreshAuctionsStatus();

        assertEquals(AuctionStatus.FINISHED, running.getStatus());
        assertTrue(auctionDAO.updatedAuctionIds.contains(302));
    }

    @Test
    void endAuctionBySellerRejectsDifferentSeller() {
        Auction auction = testAuction(401, AuctionStatus.RUNNING, LocalDateTime.now().minusMinutes(1), LocalDateTime.now().plusMinutes(1));
        auction.setSellerId("seller-1");
        auctionDAO.save(auction);

        assertThrows(
                IllegalArgumentException.class,
                () -> auctionService.endAuctionBySeller("seller-2", 401));
    }

    @Test
    void endAuctionBySellerSuccess() {
        Auction auction = testAuction(402, AuctionStatus.RUNNING, LocalDateTime.now().minusMinutes(1), LocalDateTime.now().plusMinutes(1));
        auction.setSellerId("seller-1");
        auctionDAO.save(auction);

        Auction ended = auctionService.endAuctionBySeller("seller-1", 402);

        assertEquals(AuctionStatus.FINISHED, ended.getStatus());
        assertTrue(auctionDAO.updatedAuctionIds.contains(402));
    }


    @Test
    void createAuctionRejectsNullStartTime() {
        Item item = new Electronics(500, "NullStart", "Desc", 100, "seller-1", 12);
        itemService.put(item);

        assertThrows(
                IllegalArgumentException.class,
                () -> auctionService.createAuction("seller-1", 500, null, LocalDateTime.now().plusHours(1)));
    }

    @Test
    void createAuctionRejectsNullEndTime() {
        Item item = new Electronics(501, "NullEnd", "Desc", 100, "seller-1", 12);
        itemService.put(item);

        assertThrows(
                IllegalArgumentException.class,
                () -> auctionService.createAuction("seller-1", 501, LocalDateTime.now().plusMinutes(5), null));
    }

    @Test
    void createAuctionRejectsBlankSellerId() {
        Item item = new Electronics(502, "BlankSeller", "Desc", 100, "seller-1", 12);
        itemService.put(item);

        LocalDateTime start = LocalDateTime.now().plusMinutes(5);
        LocalDateTime end = LocalDateTime.now().plusHours(1);

        assertThrows(
                IllegalArgumentException.class,
                () -> auctionService.createAuction("   ", 502, start, end));
    }

    @Test
    void createAuctionRejectsZeroItemId() {
        LocalDateTime start = LocalDateTime.now().plusMinutes(5);
        LocalDateTime end = LocalDateTime.now().plusHours(1);

        assertThrows(
                IllegalArgumentException.class,
                () -> auctionService.createAuction("seller-1", 0, start, end));
    }

    @Test
    void createAuctionRejectsHasActiveAuction() {
        Item item = new Electronics(504, "DupAuction", "Desc", 100, "seller-1", 12);
        itemService.put(item);

        LocalDateTime start1 = LocalDateTime.now().plusMinutes(5);
        LocalDateTime end1 = LocalDateTime.now().plusHours(1);

        Auction firstAuction = auctionService.createAuction("seller-1", 504, start1, end1);
        assertNotNull(firstAuction);

        LocalDateTime start2 = LocalDateTime.now().plusHours(2);
        LocalDateTime end2 = LocalDateTime.now().plusHours(5);

        assertThrows(
                IllegalArgumentException.class,
                () -> auctionService.createAuction("seller-1", 504, start2, end2));
    }

    @Test
    void endAuctionBySellerRejectsClosedAuction() {
        Auction auction = testAuction(403, AuctionStatus.FINISHED, LocalDateTime.now().minusMinutes(5), LocalDateTime.now().minusMinutes(1));
        auction.setSellerId("seller-1");
        auctionDAO.save(auction);

        assertThrows(
                IllegalArgumentException.class,
                () -> auctionService.endAuctionBySeller("seller-1", 403));
    }

    @Test
    void refreshAuctionsStatusHandlesEmptyListGracefully() {
        assertDoesNotThrow(() -> auctionService.refreshAuctionsStatus());
    }

    @Test
    void refreshAuctionsStatusDoesNotChangeFinishedAuction() {
        Auction finished = testAuction(303, AuctionStatus.FINISHED, LocalDateTime.now().minusMinutes(10), LocalDateTime.now().minusMinutes(5));
        auctionManager.addAuction(finished);

        auctionService.refreshAuctionsStatus();

        assertEquals(AuctionStatus.FINISHED, finished.getStatus());
    }


    private Auction testAuction(int id, AuctionStatus status, LocalDateTime start, LocalDateTime end) {
        Item item = new Electronics(1000 + id, "Item " + id, "Desc", 100, "seller-1", 12);
        Auction auction = new Auction(id, item, "seller-1", start, end);
        auction.setStatus(status);
        return auction;
    }

    private AuctionManager getPrivateAuctionManager(AuctionService service) {
        try {
            Field field = AuctionService.class.getDeclaredField("auctionManager");
            field.setAccessible(true);
            return (AuctionManager) field.get(service);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private void injectField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private void clearActiveAuctions(AuctionManager manager) {
        try {
            Field field = AuctionManager.class.getDeclaredField("activeAuctions");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<Auction> active = (List<Auction>) field.get(manager);
            active.clear();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private void resetAuctionManagerSingleton() {
        try {
            Field instanceField = AuctionManager.class.getDeclaredField("instance");
            instanceField.setAccessible(true);
            instanceField.set(null, null);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Cannot reset AuctionManager singleton", e);
        }
    }

    private static final class StubItemService extends ItemService {
        private final Map<Integer, Item> items = new HashMap<>();

        void put(Item item) {
            items.put(item.getId(), item);
        }

        @Override
        public Optional<Item> findById(int id) {
            return Optional.ofNullable(items.get(id));
        }
    }

    private static final class InMemoryAuctionDAO extends AuctionDAO {
        private final Map<Integer, Auction> auctions = new HashMap<>();
        private final List<Integer> updatedAuctionIds = new java.util.ArrayList<>();

        @Override
        public void save(Auction auction) {
            auctions.put(auction.getAuctionId(), auction);
        }

        @Override
        public Optional<Auction> findById(int id) {
            return Optional.ofNullable(auctions.get(id));
        }

        @Override
        public List<Auction> findAll() {
            return List.copyOf(auctions.values());
        }

        @Override
        public void update(Auction auction) {
            auctions.put(auction.getAuctionId(), auction);
            updatedAuctionIds.add(auction.getAuctionId());
        }

        @Override
        public void update(Connection conn, Auction auction) {
            auctions.put(auction.getAuctionId(), auction);
            updatedAuctionIds.add(auction.getAuctionId());
        }
    }
}
