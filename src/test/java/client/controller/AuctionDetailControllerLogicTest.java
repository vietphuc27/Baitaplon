package client.controller;

import client.application.ClientSession;
import client.network.TestSocketClient;
import common.models.auction.Auction;
import common.models.auction.AuctionStatus;
import common.models.auction.BidTransaction;
import common.models.item.Item;
import common.models.user.Bidder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import static org.junit.jupiter.api.Assertions.*;

class AuctionDetailControllerLogicTest {

    @BeforeEach
    void setUp() throws Exception {
        injectSharedSocket(new TestSocketClient());
    }

    @AfterEach
    void tearDown() {
        ClientSession.clear();
    }

    @Test
    void remainingTimeTextHandlesNullPastAndFuture() throws Exception {
        AuctionDetailController controller = new AuctionDetailController();

        assertEquals("--:--:--", invoke(controller, "remainingTimeText", new Class<?>[0]));

        Auction pastAuction = auction(1, "A", "1", LocalDateTime.now().minusSeconds(1));
        setField(controller, "auction", pastAuction);
        assertEquals("00:00:00", invoke(controller, "remainingTimeText", new Class<?>[0]));

        Auction futureAuction = auction(2, "B", "1", LocalDateTime.now().plusSeconds(3661));
        setField(controller, "auction", futureAuction);
        String remaining = String.valueOf(invoke(controller, "remainingTimeText", new Class<?>[0]));
        assertTrue(remaining.matches("\\d{2}:\\d{2}:\\d{2}"));
    }

    @Test
    void ownAuctionAndClosedStatusWork() throws Exception {
        AuctionDetailController controller = new AuctionDetailController();
        Bidder bidder = new Bidder(7, "u7", "u7@e", "p");
        setField(controller, "currentBidder", bidder);

        Auction own = auction(10, "Phone", "7", LocalDateTime.now().plusMinutes(10));
        setField(controller, "auction", own);
        assertEquals(true, invoke(controller, "isOwnAuction", new Class<?>[0]));

        own.setStatus(AuctionStatus.RUNNING);
        assertEquals(false, invoke(controller, "isAuctionClosed", new Class<?>[0]));

        own.setStatus(AuctionStatus.PAID);
        assertEquals(true, invoke(controller, "isAuctionClosed", new Class<?>[0]));
    }

    @Test
    void historySignatureAndViewOnlySetterWork() throws Exception {
        AuctionDetailController controller = new AuctionDetailController();
        assertEquals("0", invoke(controller, "buildHistorySignature", new Class<?>[] { List.class }, (Object) null));
        assertEquals("0", invoke(controller, "buildHistorySignature", new Class<?>[] { List.class }, List.of()));

        BidTransaction first = new BidTransaction(1, 10, 7, 100);
        first.setBidTime(LocalDateTime.of(2026, 5, 24, 10, 0));
        BidTransaction last = new BidTransaction(2, 10, 8, 120);
        last.setBidTime(LocalDateTime.of(2026, 5, 24, 10, 1));
        String signature = String.valueOf(invoke(controller, "buildHistorySignature", new Class<?>[] { List.class },
                List.of(first, last)));
        assertTrue(signature.startsWith("2|7|100.0|2026-05-24T10:00|8|120.0|2026-05-24T10:01"));

        controller.setViewOnly(true);
        assertEquals(true, field(controller, "viewOnly"));
        controller.setViewOnly(false);
        assertEquals(false, field(controller, "viewOnly"));
    }

    @Test
    void isAuctionClosedCoversAllStatuses() throws Exception {
        AuctionDetailController controller = new AuctionDetailController();

        assertEquals(false, invoke(controller, "isAuctionClosed", new Class<?>[0]));

        Auction au = auction(1, "X", "1", LocalDateTime.now().plusMinutes(10));
        setField(controller, "auction", au);
        assertEquals(false, invoke(controller, "isAuctionClosed", new Class<?>[0]));

        au.setStatus(AuctionStatus.RUNNING);
        assertEquals(false, invoke(controller, "isAuctionClosed", new Class<?>[0]));

        au.setStatus(AuctionStatus.OPEN);
        assertEquals(false, invoke(controller, "isAuctionClosed", new Class<?>[0]));

        au.setStatus(AuctionStatus.FINISHED);
        assertEquals(true, invoke(controller, "isAuctionClosed", new Class<?>[0]));

        au.setStatus(AuctionStatus.PAID);
        assertEquals(true, invoke(controller, "isAuctionClosed", new Class<?>[0]));

        au.setStatus(AuctionStatus.CANCELED);
        assertEquals(true, invoke(controller, "isAuctionClosed", new Class<?>[0]));
    }

    @Test
    void isOwnAuctionCoversNullAndEdgeCases() throws Exception {
        AuctionDetailController controller = new AuctionDetailController();

        assertEquals(false, invoke(controller, "isOwnAuction", new Class<?>[0]));

        Bidder bidder = new Bidder(7, "u7", "u7@e", "p");
        setField(controller, "currentBidder", bidder);
        assertEquals(false, invoke(controller, "isOwnAuction", new Class<?>[0]));

        Auction au = auction(10, "Phone", "7", LocalDateTime.now().plusMinutes(10));
        setField(controller, "auction", au);
        assertEquals(true, invoke(controller, "isOwnAuction", new Class<?>[0]));

        setField(controller, "currentBidder", new Bidder(99, "other", "o@e", "p"));
        assertEquals(false, invoke(controller, "isOwnAuction", new Class<?>[0]));

        au.setSellerId(null);
        assertEquals(false, invoke(controller, "isOwnAuction", new Class<?>[0]));
    }

    @Test
    void completeRefreshCycleHandlesQueuedFlag() throws Exception {
        AuctionDetailController controller = new AuctionDetailController();

        Auction au = auction(10, "Test", "1", LocalDateTime.now().plusMinutes(10));
        setField(controller, "auction", au);

        assertEquals(false, field(controller, "refreshInProgress"));
        assertEquals(false, field(controller, "refreshQueued"));

        // Start a refresh so refreshDataAsync sets refreshInProgress=true
        setField(controller, "refreshInProgress", true);

        // completeRefreshCycle should reset it
        invoke(controller, "completeRefreshCycle", new Class<?>[0]);

        assertEquals(false, field(controller, "refreshInProgress"));
        // refreshQueued stays false because it was false
        assertEquals(false, field(controller, "refreshQueued"));
    }

    @Test
    void closeAllWindowsNoException() throws Exception {
        invokeStatic(AuctionDetailController.class, "registerStage",
                new Class<?>[] { javafx.stage.Stage.class }, (Object) null);
        invokeStatic(AuctionDetailController.class, "closeAllWindows", new Class<?>[0]);
        invokeStatic(AuctionDetailController.class, "closeAllWindows", new Class<?>[0]);
    }

    @Test
    void pushEventIgnoresIrrelevantEventsBeforeTouchingJavaFxThread() throws Exception {
        AuctionDetailController controller = new AuctionDetailController();

        invoke(controller, "handlePushEvent", new Class<?>[] { String.class, Map.class }, "BID_PLACED", null);
        invoke(controller, "handlePushEvent", new Class<?>[] { String.class, Map.class }, "BID_PLACED", Map.of());

        Auction auction = auction(10, "Phone", "1", LocalDateTime.now().plusMinutes(10));
        setField(controller, "auction", auction);
        invoke(controller, "handlePushEvent", new Class<?>[] { String.class, Map.class },
                "BID_PLACED", Map.of("auctionId", 11));
        invoke(controller, "handlePushEvent", new Class<?>[] { String.class, Map.class },
                "UNKNOWN_EVENT", Map.of("auctionId", 10));
    }

    @Test
    void resolveSellerDisplayNameCoversLocalRemoteAndFallbackPaths() throws Exception {
        AuctionDetailController controller = new AuctionDetailController();
        assertEquals("-", invoke(controller, "resolveSellerDisplayName", new Class<?>[] { Auction.class },
                (Object) null));

        Auction withUsername = auction(1, "Phone", "7", LocalDateTime.now().plusMinutes(10));
        withUsername.setSellerUsername("seller-seven");
        assertEquals("seller-seven", invoke(controller, "resolveSellerDisplayName",
                new Class<?>[] { Auction.class }, withUsername));

        Auction blankSeller = auction(2, "Laptop", " ", LocalDateTime.now().plusMinutes(10));
        assertEquals("-", invoke(controller, "resolveSellerDisplayName",
                new Class<?>[] { Auction.class }, blankSeller));

        TestSocketClient socket = (TestSocketClient) ClientSession.getSocket();
        socket.setResponse("get_user_by_id", Map.of(
                "status", "success",
                "userId", 77,
                "username", "looked-up-seller",
                "email", "seller@e.com",
                "role", "SELLER"
        ));
        Auction fromServer = auction(3, "Camera", "77", LocalDateTime.now().plusMinutes(10));
        assertEquals("looked-up-seller", invoke(controller, "resolveSellerDisplayName",
                new Class<?>[] { Auction.class }, fromServer));
        assertEquals("looked-up-seller", fromServer.getSellerUsername());

        socket.setResponse("get_user_by_id", Map.of("status", "error", "message", "not found"));
        Auction fallback = auction(4, "Tablet", "88", LocalDateTime.now().plusMinutes(10));
        assertEquals("88", invoke(controller, "resolveSellerDisplayName",
                new Class<?>[] { Auction.class }, fallback));
    }

    @Test
    void openImagePreviewAndShutdownHandleEmptyState() throws Exception {
        AuctionDetailController controller = new AuctionDetailController();

        invoke(controller, "openImagePreview", new Class<?>[0]);
        setField(controller, "currentProductImageUrl", " ");
        invoke(controller, "openImagePreview", new Class<?>[0]);

        ExecutorService executor = (ExecutorService) field(controller, "executor");
        assertFalse(executor.isShutdown());
        invoke(controller, "shutdown", new Class<?>[0]);
        assertTrue(executor.isShutdown());
    }

    @Test
    void resolveBidderDisplayNameCoversCacheAndInvalidId() throws Exception {
        AuctionDetailController controller = new AuctionDetailController();

        String result = (String) invoke(controller, "resolveBidderDisplayName", new Class<?>[] { int.class }, 0);
        assertEquals("-", result);

        result = (String) invoke(controller, "resolveBidderDisplayName", new Class<?>[] { int.class }, -5);
        assertEquals("-", result);

        TestSocketClient socket = (TestSocketClient) ClientSession.getSocket();
        socket.setResponse("get_user_by_id", Map.of(
                "status", "success",
                "userId", 42,
                "username", "testuser42",
                "email", "test@e.com",
                "role", "BIDDER"
        ));
        result = (String) invoke(controller, "resolveBidderDisplayName", new Class<?>[] { int.class }, 42);
        assertEquals("testuser42", result);

        result = (String) invoke(controller, "resolveBidderDisplayName", new Class<?>[] { int.class }, 42);
        assertEquals("testuser42", result);
    }

    @Test
    void resolveBidderDisplayNameFallsBackToIdOnError() throws Exception {
        AuctionDetailController controller = new AuctionDetailController();

        TestSocketClient socket = (TestSocketClient) ClientSession.getSocket();
        socket.setResponse("get_user_by_id", Map.of("status", "error", "message", "not found"));

        String result = (String) invoke(controller, "resolveBidderDisplayName", new Class<?>[] { int.class }, 99);
        assertEquals("99", result);
    }

    private Auction auction(int id, String name, String sellerId, LocalDateTime endTime) {
        LocalDateTime start = endTime.minusMinutes(5);
        return new Auction(id, item(name, 10, sellerId), sellerId, start, endTime);
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

    private Object invoke(Object target, String method, Class<?>[] paramTypes, Object... args) throws Exception {
        Method m = target.getClass().getDeclaredMethod(method, paramTypes);
        m.setAccessible(true);
        return m.invoke(target, args);
    }

    private Object invokeStatic(Class<?> clazz, String method, Class<?>[] paramTypes, Object... args) throws Exception {
        Method m = clazz.getDeclaredMethod(method, paramTypes);
        m.setAccessible(true);
        return m.invoke(null, args);
    }

    private Object field(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }
}
