package client.controller;

import client.application.ClientSession;
import client.network.TestSocketClient;
import common.models.auction.Auction;
import common.models.auction.AuctionStatus;
import common.models.auction.BidTransaction;
import common.models.item.Item;
import common.models.user.Bidder;
import common.models.user.Seller;
import common.models.user.UserStatus;
import common.utils.FormatUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BidderControllerLogicTest {

    @BeforeEach
    void setUp() throws Exception {
        injectSharedSocket(new TestSocketClient());
    }

    @AfterEach
    void tearDown() {
        ClientSession.clear();
    }

    @Test
    void resolveBidResultCoversMainStates() throws Exception {
        BidderController controller = new BidderController();
        BidTransaction bid = new BidTransaction(1, 10, 5, 100);
        Auction auction = auction(10, "Laptop", "2", 50);
        auction.setCurrentLeaderId(5);

        auction.setStatus(AuctionStatus.RUNNING);
        assertEquals("Đang dẫn đầu", invoke(controller, "resolveBidResult",
                new Class<?>[] { Auction.class, BidTransaction.class }, auction, bid));

        auction.setStatus(AuctionStatus.FINISHED);
        assertEquals("Đã thắng", invoke(controller, "resolveBidResult",
                new Class<?>[] { Auction.class, BidTransaction.class }, auction, bid));

        auction.setStatus(AuctionStatus.CANCELED);
        assertEquals("Phiên đã hủy", invoke(controller, "resolveBidResult",
                new Class<?>[] { Auction.class, BidTransaction.class }, auction, bid));

        auction.setCurrentLeaderId(9);
        assertEquals("Đã bị vượt", invoke(controller, "resolveBidResult",
                new Class<?>[] { Auction.class, BidTransaction.class }, auction, bid));
    }

    @Test
    void filterAndSortAuctionsAppliesFiltersAndSort() throws Exception {
        BidderController controller = new BidderController();

        Auction low = auction(1, "Phone", "10", 20);
        low.setCurrentHighestBid(120);
        low.setStatus(AuctionStatus.RUNNING);

        Auction high = auction(2, "Laptop", "11", 30);
        high.setCurrentHighestBid(220);
        high.setStatus(AuctionStatus.RUNNING);

        Auction closed = auction(3, "Car", "12", 40);
        closed.setCurrentHighestBid(500);
        closed.setStatus(AuctionStatus.FINISHED);

        List<?> rows = (List<?>) invoke(controller, "filterAndSortAuctions",
                new Class<?>[] { List.class, String.class, String.class, String.class },
                List.of(low, high, closed), "lap", "RUNNING", "Giá cao nhất");

        assertEquals(1, rows.size());
        assertEquals("2", fieldValue(rows.getFirst(), "id"));
    }

    @Test
    void syncBidderStateAndOwnAuctionWork() throws Exception {
        BidderController controller = new BidderController();
        Bidder local = new Bidder(7, "old", "old@e", "oldpass");
        setField(controller, "currentBidder", local);

        Seller latest = new Seller(7, "newName", "new@e", "newpass");
        latest.setStatus(UserStatus.BANNED);
        latest.getWallet().setBalance(777);
        invoke(controller, "syncBidderState", new Class<?>[] { common.models.user.User.class }, latest);

        assertEquals("newName", local.getUsername());
        assertEquals("newpass", local.getPassword());
        assertEquals(UserStatus.BANNED, local.getStatus());
        assertEquals(777, local.getWallet().getBalance(), 0.001);

        Auction ownAuction = auction(55, "TV", "7", 10);
        Auction otherAuction = auction(56, "PC", "99", 10);
        assertTrue((boolean) invoke(controller, "isOwnAuction", new Class<?>[] { Auction.class }, ownAuction));
        assertEquals(false, invoke(controller, "isOwnAuction", new Class<?>[] { Auction.class }, otherAuction));
    }

    @Test
    void resolveComparatorAndToAuctionRowCoverPriceAndStatusBranches() throws Exception {
        BidderController controller = new BidderController();
        LocalDateTime now = LocalDateTime.now();

        Auction low = new Auction(1, item("Phone", 100, "10"), "10", now.minusHours(3), now.plusHours(3));
        low.setCurrentHighestBid(100);

        Auction high = new Auction(2, item("Laptop", 200, "11"), "11", now.minusHours(1), now.plusHours(1));
        high.setCurrentHighestBid(200);

        Auction mid = new Auction(3, item("Tablet", 150, "12"), "12", now.minusHours(2), now.plusHours(2));
        mid.setCurrentHighestBid(150);

        Comparator<Auction> desc = (Comparator<Auction>) invoke(controller, "resolveComparator",
                new Class<?>[] { String.class }, "Giá cao nhất");
        assertEquals(2, List.of(low, high, mid).stream().sorted(desc).findFirst().orElseThrow().getAuctionId());

        Comparator<Auction> asc = (Comparator<Auction>) invoke(controller, "resolveComparator",
                new Class<?>[] { String.class }, "Giá thấp nhất");
        assertEquals(1, List.of(low, high, mid).stream().sorted(asc).findFirst().orElseThrow().getAuctionId());

        Comparator<Auction> endingSoon = (Comparator<Auction>) invoke(controller, "resolveComparator",
                new Class<?>[] { String.class }, "Sắp kết thúc");
        assertEquals(2,
                List.of(low, high, mid).stream().sorted(endingSoon).findFirst().orElseThrow().getAuctionId());

        Comparator<Auction> newest = (Comparator<Auction>) invoke(controller, "resolveComparator",
                new Class<?>[] { String.class }, "Mới nhất");
        assertEquals(2, List.of(low, high, mid).stream().sorted(newest).findFirst().orElseThrow().getAuctionId());

        Auction zeroBid = auction(9, "Speaker", null, 333);
        zeroBid.setCurrentHighestBid(0);
        zeroBid.setStatus(null);
        Object row = invoke(controller, "toAuctionRow", new Class<?>[] { Auction.class }, zeroBid);
        assertEquals(FormatUtils.formatCurrency(333), fieldValue(row, "currentPrice"));
        assertEquals("-", fieldValue(row, "status"));
        assertEquals("-", fieldValue(row, "seller"));
    }

    @Test
    void historyKeywordAndFindAuctionSafeCoverCacheServerAndErrorPaths() throws Exception {
        TestSocketClient socket = (TestSocketClient) ClientSession.getSocket();
        BidderController controller = new BidderController();

        BidderController.BidHistoryRow row = new BidderController.BidHistoryRow("55", "Gaming Laptop", "100", "t",
                "Đang dẫn đầu");
        assertEquals(true, invoke(controller, "matchesHistoryKeyword",
                new Class<?>[] { BidderController.BidHistoryRow.class, String.class }, row, "lap"));
        assertEquals(false, invoke(controller, "matchesHistoryKeyword",
                new Class<?>[] { BidderController.BidHistoryRow.class, String.class }, row, "phone"));

        Auction cached = auction(10, "Cache", "1", 10);
        setField(controller, "cachedAuctions", List.of(cached));
        Auction fromCache = (Auction) invoke(controller, "findAuctionSafe", new Class<?>[] { int.class }, 10);
        assertEquals(10, fromCache.getAuctionId());

        socket.setResponse("get_auction_by_id", Map.of(
                "status", "success",
                "auctionId", 88,
                "itemName", "ServerAuction",
                "sellerId", "s1",
                "auctionStatus", "OPEN",
                "currentPrice", 0.0));
        setField(controller, "cachedAuctions", List.of());
        Auction fromServer = (Auction) invoke(controller, "findAuctionSafe", new Class<?>[] { int.class }, 88);
        assertEquals(88, fromServer.getAuctionId());

        socket.setResponse("get_auction_by_id", Map.of("status", "error", "message", "not found"));
        assertNull(invoke(controller, "findAuctionSafe", new Class<?>[] { int.class }, 999));
    }

    private Auction auction(int id, String name, String sellerId, double startingPrice) {
        LocalDateTime now = LocalDateTime.now();
        return new Auction(id, item(name, startingPrice, sellerId), sellerId, now.minusMinutes(1), now.plusMinutes(10));
    }

    private Item item(String name, double startingPrice, String sellerId) {
        return new Item(1, name, "d", startingPrice, sellerId) {
            @Override
            public String getInfo() {
                return name;
            }
        };
    }

    private void injectSharedSocket(TestSocketClient socketClient) throws Exception {
        Field field = ClientSession.class.getDeclaredField("sharedSocket");
        field.setAccessible(true);
        field.set(null, socketClient);
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private String fieldValue(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return String.valueOf(field.get(target));
    }

    private Object invoke(Object target, String method, Class<?>[] paramTypes, Object... args) throws Exception {
        Method m = target.getClass().getDeclaredMethod(method, paramTypes);
        m.setAccessible(true);
        return m.invoke(target, args);
    }
}
