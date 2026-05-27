package client.controller;

import client.application.ClientSession;
import client.network.TestSocketClient;
import common.models.auction.Auction;
import common.models.auction.AuctionStatus;
import common.models.item.Item;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SellerControllerLogicTest {

    @BeforeEach
    void setUp() throws Exception {
        injectSharedSocket(new TestSocketClient());
    }

    @AfterEach
    void tearDown() {
        ClientSession.clear();
    }

    @Test
    void textAndIntegerValidationWork() throws Exception {
        SellerController controller = new SellerController();

        assertTrue((boolean) invoke(controller, "hasText", new Class<?>[] { String.class }, " abc "));
        assertFalse((boolean) invoke(controller, "hasText", new Class<?>[] { String.class }, "  "));
        assertTrue((boolean) invoke(controller, "isNonNegativeInteger", new Class<?>[] { String.class }, "0"));
        assertFalse((boolean) invoke(controller, "isNonNegativeInteger", new Class<?>[] { String.class }, "-1"));
        assertFalse((boolean) invoke(controller, "isNonNegativeInteger", new Class<?>[] { String.class }, "x"));
    }

    @Test
    void auctionKeywordAndComparatorWork() throws Exception {
        SellerController controller = new SellerController();

        Auction phone = auction(1, "Phone", "10", 20);
        phone.setCurrentHighestBid(100);
        phone.setStatus(AuctionStatus.RUNNING);

        Auction laptop = auction(2, "Laptop", "10", 20);
        laptop.setCurrentHighestBid(300);
        laptop.setStatus(AuctionStatus.RUNNING);

        assertTrue((boolean) invoke(controller, "matchesAuctionKeyword",
                new Class<?>[] { Auction.class, String.class }, phone, "pho"));
        assertFalse((boolean) invoke(controller, "matchesAuctionKeyword",
                new Class<?>[] { Auction.class, String.class }, phone, "car"));

        Comparator<Auction> comparator = (Comparator<Auction>) invoke(controller, "resolveAuctionComparator",
                new Class<?>[] { String.class }, "Giá giảm");
        assertTrue(comparator.compare(phone, laptop) > 0);
    }

    @Test
    void imageDataUriBuilderUsesProvidedMimeType() throws Exception {
        SellerController controller = new SellerController();

        String dataUri = (String) invoke(controller, "buildImageDataUri",
                new Class<?>[] { byte[].class, String.class },
                "hello".getBytes(StandardCharsets.UTF_8), "image/png");

        assertTrue(dataUri.startsWith("data:image/png;base64,"));
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

    private Object invoke(Object target, String method, Class<?>[] paramTypes, Object... args) throws Exception {
        Method m = target.getClass().getDeclaredMethod(method, paramTypes);
        m.setAccessible(true);
        return m.invoke(target, args);
    }
}
