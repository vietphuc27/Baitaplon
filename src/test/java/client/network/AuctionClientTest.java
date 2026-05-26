package client.network;

import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuctionClientTest {

    @Test
    void createAuctionRejectsNullAndInvalidInputs() {
        AuctionClient client = new AuctionClient();

        assertThrows(IllegalArgumentException.class, () -> client.createAuction(null));

        AuctionClient.CreateAuctionRequest badPrice = new AuctionClient.CreateAuctionRequest(
                "seller-1", "Item", "art", 0.0, "desc", LocalDateTime.now().plusHours(1),
                "artist", "", "", "", "", "", "", "", "");
        assertThrows(IllegalArgumentException.class, () -> client.createAuction(badPrice));

        AuctionClient.CreateAuctionRequest badTime = new AuctionClient.CreateAuctionRequest(
                "seller-1", "Item", "art", 10.0, "desc", LocalDateTime.now().minusMinutes(1),
                "artist", "", "", "", "", "", "", "", "");
        assertThrows(IllegalArgumentException.class, () -> client.createAuction(badTime));
    }

    @Test
    void endAuctionRejectsInvalidAuctionIdBeforeServiceCall() {
        AuctionClient client = new AuctionClient();
        assertThrows(IllegalArgumentException.class, () -> client.endAuction("seller-1", "abc"));
        assertThrows(IllegalArgumentException.class, () -> client.endAuction("seller-1", "0"));
    }

    @Test
    void endAuctionRejectsBlankSellerIdBeforeServiceCall() {
        AuctionClient client = new AuctionClient();
        assertThrows(IllegalArgumentException.class, () -> client.endAuction("  ", "10"));
    }

    @Test
    void normalizeTypeAndBuildDescriptionHandleVietnameseValues() {
        AuctionClient client = new AuctionClient();
        AuctionClient.CreateAuctionRequest request = new AuctionClient.CreateAuctionRequest(
                "seller-1", "Xe", "Phương tiện", 100.0, "mo ta", LocalDateTime.now().plusHours(1),
                "", "", "", "", "", "", " Honda ", "12000", "2022");

        assertEquals("electronics", invoke(client, "normalizeType", new Class<?>[] { String.class }, "điện tử"));
        assertEquals("art", invoke(client, "normalizeType", new Class<?>[] { String.class }, "nghệ thuật"));
        assertEquals("art", invoke(client, "normalizeType", new Class<?>[] { String.class }, "art"));
        assertEquals("vehicle", invoke(client, "normalizeType", new Class<?>[] { String.class }, "vehicle"));
        assertEquals("vehicle", invoke(client, "normalizeType", new Class<?>[] { String.class }, "phương tiện"));
        assertEquals("electronics", invoke(client, "normalizeType", new Class<?>[] { String.class }, "electronic"));
        assertEquals("unknown", invoke(client, "normalizeType", new Class<?>[] { String.class }, "unknown"));

        String description = (String) invoke(client, "buildDescription",
                new Class<?>[] { AuctionClient.CreateAuctionRequest.class }, request);

        assertTrue(description.contains("Hang xe: Honda"));
        assertTrue(description.contains("So km da di: 12000"));
    }

    @Test
    void buildItemCreatesExpectedSubtypeAndRejectsNegativeMileage() {
        AuctionClient client = new AuctionClient();
        AuctionClient.CreateAuctionRequest artReq = new AuctionClient.CreateAuctionRequest(
                "seller-1", "Painting", "art", 100.0, "desc", LocalDateTime.now().plusHours(1),
                "Artist A", "", "", "", "", "", "", "", "");

        Object artItem = invoke(client, "buildItem",
                new Class<?>[] { int.class, AuctionClient.CreateAuctionRequest.class }, 100001, artReq);
        assertInstanceOf(common.models.item.Art.class, artItem);

        AuctionClient.CreateAuctionRequest electReq = new AuctionClient.CreateAuctionRequest(
                "seller-1", "Laptop", "electronics", 200.0, "desc", LocalDateTime.now().plusHours(1),
                "", "", "", "Dell", "XPS", "good", "", "", "");
        Object electItem = invoke(client, "buildItem",
                new Class<?>[] { int.class, AuctionClient.CreateAuctionRequest.class }, 100002, electReq);
        assertInstanceOf(common.models.item.Electronics.class, electItem);

        AuctionClient.CreateAuctionRequest badVehicle = new AuctionClient.CreateAuctionRequest(
                "seller-1", "Car", "vehicle", 100.0, "desc", LocalDateTime.now().plusHours(1),
                "", "", "", "", "", "", "Toyota", "-5", "2020");
        assertThrows(IllegalArgumentException.class, () -> invoke(client, "buildItem",
                new Class<?>[] { int.class, AuctionClient.CreateAuctionRequest.class }, 100003, badVehicle));
    }

    private Object invoke(Object target, String methodName, Class<?>[] paramTypes, Object... args) {
        try {
            Method method = target.getClass().getDeclaredMethod(methodName, paramTypes);
            method.setAccessible(true);
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new RuntimeException(cause);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}