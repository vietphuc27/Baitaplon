package client.network;

import common.models.auction.Auction;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SellerClientTest {

    @Test
    void constructorRejectsNullSocket() {
        assertThrows(IllegalArgumentException.class, () -> new SellerClient(null));
    }

    @Test
    void createAuctionRejectsInvalidRequest() {
        SellerClient client = new SellerClient(new TestSocketClient());
        SellerClient.CreateAuctionRequest invalid = new SellerClient.CreateAuctionRequest(
                "seller-1", "Item", "art", 10.0, "desc", LocalDateTime.now().minusMinutes(1),
                "artist", "", "", "", "", "", "", "", "");
        assertThrows(IllegalArgumentException.class, () -> client.createAuction(invalid));
    }

    @Test
    void createAuctionBuildsMinimalAuctionFromResponse() {
        TestSocketClient socket = new TestSocketClient();
        socket.setResponse("create_auction", Map.of("status", "success", "auctionId", 777));
        SellerClient client = new SellerClient(socket);
        SellerClient.CreateAuctionRequest request = new SellerClient.CreateAuctionRequest(
                "seller-1", "Painting", "art", 100.0, "desc", LocalDateTime.now().plusHours(1),
                "Artist", "2020", "Oil", "", "", "", "", "", "");

        Auction auction = client.createAuction(request);

        assertEquals(777, auction.getAuctionId());
        assertEquals("seller-1", auction.getSellerId());
        assertEquals("Painting", auction.getItem().getName());
    }

    @Test
    void createAuctionIncludesOnlyNonBlankOptionalFields() {
        TestSocketClient socket = new TestSocketClient();
        socket.setResponse("create_auction", Map.of("status", "success", "auctionId", 778));
        SellerClient client = new SellerClient(socket);
        SellerClient.CreateAuctionRequest request = new SellerClient.CreateAuctionRequest(
                "seller-1", "Car", "vehicle", 300.0, "desc", LocalDateTime.now().plusHours(2),
                "", "", "", "", "", "", "  Toyota  ", " 120000 ", "");

        client.createAuction(request);
        Map<String, Object> payload = socket.getPayloadOf("create_auction");

        assertEquals("Toyota", payload.get("vehicleBrand"));
        assertEquals("120000", payload.get("mileage"));
        assertEquals(false, payload.containsKey("artist"));
    }

    @Test
    void createAuctionFailsWhenAuctionIdMissingInSuccessResponse() {
        TestSocketClient socket = new TestSocketClient();
        socket.setResponse("create_auction", Map.of("status", "success"));
        SellerClient client = new SellerClient(socket);
        SellerClient.CreateAuctionRequest request = new SellerClient.CreateAuctionRequest(
                "seller-1", "Painting", "art", 100.0, "desc", LocalDateTime.now().plusHours(1),
                "Artist", "", "", "", "", "", "", "", "");
        assertThrows(RuntimeException.class, () -> client.createAuction(request));
    }

    @Test
    void getSellerAuctionsParsesAndSkipsInvalidRows() {
        TestSocketClient socket = new TestSocketClient();
        socket.setResponse("get_seller_auctions", Map.of(
                "status", "success",
                "auctions", List.of(
                        Map.of("auctionId", 1, "itemName", "A", "sellerId", "s1", "auctionStatus", "RUNNING", "currentPrice", 50.0),
                        Map.of("auctionId", "bad"))));
        SellerClient client = new SellerClient(socket);

        List<Auction> auctions = client.getSellerAuctions("s1");
        assertEquals(1, auctions.size());
        assertEquals(1, auctions.getFirst().getAuctionId());
    }

    @Test
    void getSellerAuctionsReturnsEmptyWhenNoAuctionsField() {
        TestSocketClient socket = new TestSocketClient();
        socket.setResponse("get_seller_auctions", Map.of("status", "success"));
        SellerClient client = new SellerClient(socket);
        assertTrue(client.getSellerAuctions("s1").isEmpty());
    }

    @Test
    void createAuctionAndGetSellerAuctionsServerErrorPaths() {
        TestSocketClient socket = new TestSocketClient();
        socket.setResponse("create_auction", Map.of("status", "error", "message", "server down"));
        socket.setResponse("get_seller_auctions", Map.of("status", "error"));

        SellerClient client = new SellerClient(socket);
        SellerClient.CreateAuctionRequest request = new SellerClient.CreateAuctionRequest(
                "s1", "Item", "art", 100.0, "desc", LocalDateTime.now().plusHours(1),
                "A", "", "", "", "", "", "", "", "");
        assertThrows(RuntimeException.class, () -> client.createAuction(request));
        assertThrows(RuntimeException.class, () -> client.getSellerAuctions("s1"));
    }

    @Test
    void endAuctionAndFailurePath() {
        TestSocketClient socket = new TestSocketClient();
        socket.setResponse("end_auction", Map.of("status", "success"));
        SellerClient client = new SellerClient(socket);
        client.endAuction("seller-1", "10");
        assertEquals(List.of("end_auction"), socket.getSyncActions());

        socket.setResponse("end_auction", Map.of("status", "error", "message", "denied"));
        assertThrows(RuntimeException.class, () -> client.endAuction("seller-1", "11"));
        assertTrue(socket.getSyncActions().size() >= 2);
    }
}
