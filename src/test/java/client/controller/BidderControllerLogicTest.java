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
import java.util.concurrent.ExecutorService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

        assertEquals("Đã bị vượt", invoke(controller, "resolveBidResult",
                new Class<?>[] { Auction.class, BidTransaction.class }, null, bid));

        auction.setCurrentLeaderId(null);
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
        assertEquals(false, invoke(controller, "isOwnAuction", new Class<?>[] { Auction.class }, (Object) null));
        otherAuction.setSellerId(null);
        assertEquals(false, invoke(controller, "isOwnAuction", new Class<?>[] { Auction.class }, otherAuction));

        Bidder latestBidder = new Bidder(7, "bidderName", "bidder@e", "bidderpass");
        latestBidder.getWallet().setBalance(888);
        invoke(controller, "syncBidderState", new Class<?>[] { common.models.user.User.class }, latestBidder);
        assertEquals("bidderName", local.getUsername());
        assertEquals(888, local.getWallet().getBalance(), 0.001);
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

    @Test
    void sellerDisplayNameAndAuctionFilteringCoverSellerBranches() throws Exception {
        TestSocketClient socket = (TestSocketClient) ClientSession.getSocket();
        BidderController controller = new BidderController();

        assertEquals("-", invoke(controller, "resolveSellerDisplayName",
                new Class<?>[] { Auction.class }, (Object) null));

        Auction namedSeller = auction(1, "Phone", "21", 10);
        namedSeller.setSellerUsername("seller-name");
        assertEquals("seller-name", invoke(controller, "resolveSellerDisplayName",
                new Class<?>[] { Auction.class }, namedSeller));

        Auction blankSeller = auction(2, "Laptop", " ", 20);
        assertEquals("-", invoke(controller, "resolveSellerDisplayName",
                new Class<?>[] { Auction.class }, blankSeller));

        socket.setResponse("get_user_by_id", Map.of(
                "status", "success",
                "userId", 42,
                "username", "remote-seller",
                "email", "remote@e.com",
                "role", "SELLER"
        ));
        Auction remoteSeller = auction(3, "Tablet", "42", 30);
        assertEquals("remote-seller", invoke(controller, "resolveSellerDisplayName",
                new Class<?>[] { Auction.class }, remoteSeller));
        assertEquals("remote-seller", remoteSeller.getSellerUsername());

        Auction cachedSeller = auction(4, "Camera", "42", 40);
        assertEquals("remote-seller", invoke(controller, "resolveSellerDisplayName",
                new Class<?>[] { Auction.class }, cachedSeller));

        socket.setResponse("get_user_by_id", Map.of("status", "error", "message", "not found"));
        Auction fallbackSeller = auction(5, "Speaker", "99", 50);
        assertEquals("99", invoke(controller, "resolveSellerDisplayName",
                new Class<?>[] { Auction.class }, fallbackSeller));

        Auction sellerUsernameMatch = auction(6, "Watch", "seller-a", 60);
        sellerUsernameMatch.setSellerUsername("Alpha Seller");
        sellerUsernameMatch.setStatus(AuctionStatus.OPEN);
        Auction sellerIdMatch = auction(7, "Keyboard", "seller-b", 70);
        sellerIdMatch.setStatus(AuctionStatus.RUNNING);
        Auction nullItem = new Auction(8, null, "seller-c",
                LocalDateTime.now().minusMinutes(1), LocalDateTime.now().plusMinutes(10));

        List<?> usernameRows = (List<?>) invoke(controller, "filterAndSortAuctions",
                new Class<?>[] { List.class, String.class, String.class, String.class },
                List.of(sellerUsernameMatch, sellerIdMatch, nullItem), "alpha", "Tất cả", "Mới nhất");
        assertEquals(1, usernameRows.size());
        assertEquals("6", fieldValue(usernameRows.getFirst(), "id"));

        List<?> sellerIdRows = (List<?>) invoke(controller, "filterAndSortAuctions",
                new Class<?>[] { List.class, String.class, String.class, String.class },
                List.of(sellerUsernameMatch, sellerIdMatch, nullItem), "seller-b", "RUNNING", "Mới nhất");
        assertEquals(1, sellerIdRows.size());
        assertEquals("7", fieldValue(sellerIdRows.getFirst(), "id"));
    }

    @Test
    void bidHistoryRowMappingAndShutdownCoverRemainingPureLogic() throws Exception {
        BidderController controller = new BidderController();

        Auction wonAuction = auction(20, "Console", "1", 100);
        wonAuction.setCurrentLeaderId(5);
        wonAuction.setStatus(AuctionStatus.PAID);
        setField(controller, "cachedAuctions", List.of(wonAuction));

        BidTransaction winningBid = new BidTransaction(1, 20, 5, 150);
        winningBid.setBidTime(LocalDateTime.of(2026, 6, 1, 9, 30));
        Object wonRow = invoke(controller, "toBidHistoryRow", new Class<?>[] { BidTransaction.class }, winningBid);
        assertEquals("20", fieldValue(wonRow, "auctionId"));
        assertEquals("Console", fieldValue(wonRow, "itemName"));
        assertEquals("Đã thắng", fieldValue(wonRow, "result"));

        TestSocketClient socket = (TestSocketClient) ClientSession.getSocket();
        socket.setResponse("get_auction_by_id", Map.of("status", "error", "message", "not found"));
        setField(controller, "cachedAuctions", List.of());
        BidTransaction unknownBid = new BidTransaction(2, 999, 5, 50);
        Object unknownRow = invoke(controller, "toBidHistoryRow", new Class<?>[] { BidTransaction.class }, unknownBid);
        assertEquals("-", fieldValue(unknownRow, "itemName"));
        assertEquals("Đã bị vượt", fieldValue(unknownRow, "result"));

        ExecutorService executor = (ExecutorService) field(controller, "backgroundExecutor");
        assertFalse(executor.isShutdown());
        invoke(controller, "shutdown", new Class<?>[0]);
        assertTrue(executor.isShutdown());
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

    private Object field(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    private Object invoke(Object target, String method, Class<?>[] paramTypes, Object... args) throws Exception {
        Method m = target.getClass().getDeclaredMethod(method, paramTypes);
        m.setAccessible(true);
        return m.invoke(target, args);
    }
}
