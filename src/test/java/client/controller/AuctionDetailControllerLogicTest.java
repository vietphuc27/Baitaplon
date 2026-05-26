package client.controller;

import common.models.auction.Auction;
import common.models.auction.AuctionStatus;
import common.models.auction.BidTransaction;
import common.models.item.Item;
import common.models.user.Bidder;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuctionDetailControllerLogicTest {

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

    private Object field(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }
}
