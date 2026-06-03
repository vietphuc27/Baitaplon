package client.network;

import client.application.ClientSession;
import common.models.auction.Auction;
import common.models.auction.BidTransaction;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class BidClientTest {

    @Test
    void constructorRejectsNullSocket() {
        assertThrows(IllegalArgumentException.class, () -> new BidClient(null));
    }

    @Test
    void getAllAuctionsParsesValidAndSkipsInvalid() {
        TestSocketClient socket = new TestSocketClient();
        socket.setResponse("get_all_auctions", Map.of(
                "status", "success",
                "auctions", List.of(
                        Map.of(
                                "auctionId", "10",
                                "itemName", "Laptop",
                                "description", "D",
                                "sellerId", "s1",
                                "auctionStatus", "RUNNING",
                                "currentPrice", 100.0,
                                "startingPrice", 50.0,
                                "currentLeaderId", "null"),
                        Map.of("auctionId", "bad-id"))));

        BidClient client = new BidClient(socket);
        List<Auction> auctions = client.getAllAuctions();

        assertEquals(1, auctions.size());
        assertEquals(10, auctions.getFirst().getAuctionId());
        assertEquals(100.0, auctions.getFirst().getCurrentHighestBid());
        assertEquals(null, auctions.getFirst().getCurrentLeaderId());
    }

    @Test
    void findAuctionByIdReturnsOptional() {
        TestSocketClient socket = new TestSocketClient();
        socket.setResponse("get_auction_by_id", Map.of(
                "status", "success",
                "auctionId", 22,
                "itemName", "Phone",
                "sellerId", "s2",
                "auctionStatus", "OPEN",
                "currentPrice", 0.0));

        BidClient client = new BidClient(socket);
        assertTrue(client.findAuctionById(22).isPresent());
    }

    @Test
    void parseBidHistoryAndRegisterCancelAutoBid() {
        TestSocketClient socket = new TestSocketClient();
        socket.setResponse("get_bidder_bid_history", Map.of(
                "status", "success",
                "bids", List.of(
                        Map.of("bidId", 1, "auctionId", 2, "bidderId", 3, "bidAmount", 44.5, "bidTime", "2026-05-24T20:00:00"),
                        Map.of("bidId", 2, "auctionId", 2, "bidderId", 3, "bidAmount", 45.5, "bidTime", "not-time"))));
        socket.setResponse("register_auto_bid", Map.of("status", "success", "agentId", 9));
        socket.setResponse("cancel_auto_bid", Map.of("status", "success"));

        BidClient client = new BidClient(socket);
        List<BidTransaction> bids = client.getBidderBidHistory("3");
        int agentId = client.registerAutoBid(3, 2, 99.0, 1.0);
        boolean canceled = client.cancelAutoBid(agentId);

        assertEquals(2, bids.size());
        assertEquals(1, bids.getFirst().getId());
        assertEquals(9, agentId);
        assertTrue(canceled);
    }

    @Test
    void getAuctionBidHistoryAndCancelAutoBidAndDepositError() {
        TestSocketClient socket = new TestSocketClient();
        socket.setResponse("get_auction_bid_history", Map.of(
                "status", "success",
                "bids", List.of(
                        Map.of("bidId", 1, "auctionId", 2, "bidderId", 3, "bidAmount", 44.5, "bidTime", "2026-05-24T20:00:00"))));
        socket.setResponse("cancel_auto_bid", Map.of("status", "error"));
        socket.setResponse("update_wallet_deposit", Map.of("status", "error", "message", "no funds"));
        socket.setResponse("refresh_auctions_status", Map.of("status", "error"));
        socket.setResponse("register_auto_bid", Map.of("status", "success"));

        BidClient client = new BidClient(socket);
        assertEquals(1, client.getAuctionBidHistory("2").size());
        assertFalse(client.cancelAutoBid(99));
        assertThrows(RuntimeException.class, () -> client.deposit(8, 500.0));
        assertThrows(RuntimeException.class, client::refreshAuctionsStatus);
        assertEquals(-1, client.registerAutoBid(3, 2, 99.0, 1.0));
    }

    @Test
    void placeBidAndDepositSendRequests() {
        TestSocketClient socket = new TestSocketClient();
        socket.setResponse("place_bid", Map.of("status", "success"));
        socket.setResponse("update_wallet_deposit", Map.of("status", "success"));
        socket.setResponse("refresh_auctions_status", Map.of("status", "success"));
        socket.setResponse("get_auto_bid_status", Map.of("status", "success", "enabled", true, "agentId", 9));
        BidClient client = new BidClient(socket);
        ClientSession.setAuthToken("jwt-token-8");

        client.placeBid("101", 200.0);
        client.deposit(8, 500.0);
        client.refreshAuctionsStatus();
        Map<String, Object> status = client.getAutoBidStatus(8, 101);

        assertEquals(List.of("place_bid", "update_wallet_deposit", "refresh_auctions_status", "get_auto_bid_status"),
                socket.getSyncActions());
        assertEquals("jwt-token-8", socket.getPayloadOf("place_bid").get("token"));
        assertEquals("8", socket.getPayloadOf("update_wallet_deposit").get("userId"));
        assertEquals(true, status.get("enabled"));
    }
}
