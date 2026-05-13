package server.service;

import common.exceptions.AuctionClosedException;
import common.exceptions.InvalidBidException;
import common.models.auction.Auction;
import common.models.auction.AuctionStatus;
import common.models.auction.BidTransaction;
import common.models.item.Art;
import common.models.item.Item;
import common.models.user.Bidder;
import common.models.user.User;
import server.manager.AuctionManager;
import server.repository.AuctionDAO;
import server.repository.BidTransactionDAO;
import server.repository.UserDAO;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class BidServiceTest {
    private AuctionManager auctionManager;
    private InMemoryAuctionDAO auctionDAO;
    private InMemoryBidTransactionDAO bidTransactionDAO;
    private InMemoryUserDAO userDAO;
    private BidService bidService;

    @BeforeEach
    void setUp() {
        auctionManager = AuctionManager.getInstance();
        auctionManager.getAllActiveAuctions().clear();
        auctionDAO = new InMemoryAuctionDAO();
        bidTransactionDAO = new InMemoryBidTransactionDAO();
        userDAO = new InMemoryUserDAO();
        bidService = new BidService(auctionManager, auctionDAO, bidTransactionDAO, userDAO);
    }

    @AfterEach
    void tearDown() {
        auctionManager.getAllActiveAuctions().clear();
    }

    @Test
    void placeBidRejectsNullAuction() {
        Bidder bidder = createBidder(1, 200.0);

        assertThrows(IllegalArgumentException.class, () -> bidService.placeBid((Auction) null, bidder, 100.0));
    }

    @Test
    void placeBidRejectsNullBidder() {
        Auction auction = createRunningAuction(1, 100.0);

        assertThrows(IllegalArgumentException.class, () -> bidService.placeBid(auction, null, 100.0));
    }

    @Test
    void placeBidRejectsAuctionWithoutItem() {
        Auction auction = createRunningAuction(2, 100.0);
        auction.setItem(null);
        Bidder bidder = createBidder(2, 200.0);

        assertThrows(InvalidBidException.class, () -> bidService.placeBid(auction, bidder, 100.0));
    }

    @Test
    void placeBidRejectsFirstBidBelowStartingPrice() {
        Auction auction = createRunningAuction(3, 100.0);
        Bidder bidder = createBidder(3, 200.0);

        assertThrows(InvalidBidException.class, () -> bidService.placeBid(auction, bidder, 99.99));
    }

    @Test
    void placeBidAcceptsFirstBidAtStartingPrice() {
        Auction auction = createRunningAuction(4, 100.0);
        Bidder bidder = createBidder(4, 150.0);

        BidTransaction bid = bidService.placeBid(auction, bidder, 100.0);

        assertNotNull(bid);
        assertEquals(100.0, bid.getBidAmount(), 0.0001);
        assertEquals(100.0, auction.getCurrentHighestBid(), 0.0001);
        assertSame(bidder, auction.getCurrentLeader());
        assertEquals(50.0, bidder.getWallet().getBalance(), 0.0001);
        assertEquals(1, bidTransactionDAO.findByAuctionId(4).size());
        assertEquals(100.0, bidService.getCurrentHighestBid("4").getBidAmount(), 0.0001);
    }

    @Test
    void placeBidRejectsSecondBidEqualToCurrentHighestBid() {
        Auction auction = createRunningAuction(5, 100.0);
        Bidder firstBidder = createBidder(5, 200.0);
        Bidder secondBidder = createBidder(6, 200.0);

        bidService.placeBid(auction, firstBidder, 100.0);

        assertThrows(InvalidBidException.class, () -> bidService.placeBid(auction, secondBidder, 100.0));
        assertEquals(100.0, auction.getCurrentHighestBid(), 0.0001);
        assertSame(firstBidder, auction.getCurrentLeader());
        assertEquals(1, bidTransactionDAO.findByAuctionId(5).size());
    }

    @Test
    void placeBidAcceptsSecondBidJustAboveCurrentHighestBid() {
        Auction auction = createRunningAuction(6, 100.0);
        Bidder firstBidder = createBidder(7, 200.0);
        Bidder secondBidder = createBidder(8, 200.0);

        bidService.placeBid(auction, firstBidder, 100.0);
        BidTransaction secondBid = bidService.placeBid(auction, secondBidder, 100.01);

        assertNotNull(secondBid);
        assertEquals(100.01, auction.getCurrentHighestBid(), 0.0001);
        assertSame(secondBidder, auction.getCurrentLeader());
        assertEquals(2, bidTransactionDAO.findByAuctionId(6).size());
        assertEquals(100.01, bidService.getCurrentHighestBid("6").getBidAmount(), 0.0001);
    }

    @Test
    void placeBidRejectsLaterBidBelowStartingPriceEvenIfAboveCurrentHighestBid() {
        Auction auction = createRunningAuction(11, 100.0);
        Bidder firstBidder = createBidder(14, 500.0);
        Bidder secondBidder = createBidder(15, 500.0);

        bidService.placeBid(auction, firstBidder, 100.0);

        assertThrows(InvalidBidException.class, () -> bidService.placeBid(auction, secondBidder, 101.0));
        assertEquals(100.0, auction.getCurrentHighestBid(), 0.0001);
        assertSame(firstBidder, auction.getCurrentLeader());
        assertEquals(1, bidTransactionDAO.findByAuctionId(11).size());
    }

    @Test
    void placeBidRejectsWhenBalanceIsBelowBidAmount() {
        Auction auction = createRunningAuction(7, 50.0);
        Bidder bidder = createBidder(9, 49.99);

        assertThrows(InvalidBidException.class, () -> bidService.placeBid(auction, bidder, 50.0));
        assertEquals(49.99, bidder.getWallet().getBalance(), 0.0001);
        assertTrue(bidTransactionDAO.findByAuctionId(7).isEmpty());
    }

    @Test
    void placeBidAcceptsWhenBalanceEqualsBidAmount() {
        Auction auction = createRunningAuction(8, 50.0);
        Bidder bidder = createBidder(10, 50.0);

        BidTransaction bid = bidService.placeBid(auction, bidder, 50.0);

        assertNotNull(bid);
        assertEquals(0.0, bidder.getWallet().getBalance(), 0.0001);
        assertEquals(50.0, auction.getCurrentHighestBid(), 0.0001);
        assertEquals(1, bidTransactionDAO.findByAuctionId(8).size());
    }

    @Test
    void placeBidRejectsClosedAuction() {
        Auction auction = createRunningAuction(9, 100.0);
        auction.setStatus(AuctionStatus.FINISHED);
        Bidder bidder = createBidder(11, 200.0);

        assertThrows(AuctionClosedException.class, () -> bidService.placeBid(auction, bidder, 100.0));
    }

    @Test
    void placeBidRejectsNonNumericAuctionId() {
        Bidder bidder = createBidder(12, 200.0);

        assertThrows(IllegalArgumentException.class, () -> bidService.placeBid("abc", bidder, 100.0));
    }

    @Test
    void placeBidResolvesAuctionByStringId() {
        Auction auction = createRunningAuction(10, 100.0);
        auctionDAO.save(auction);
        Bidder bidder = createBidder(13, 200.0);

        BidTransaction bid = bidService.placeBid("10", bidder, 100.0);

        assertNotNull(bid);
        assertEquals(100.0, bid.getBidAmount(), 0.0001);
        assertEquals(1, bidTransactionDAO.findByAuctionId(10).size());
        assertEquals(100.0, bidService.getCurrentHighestBid("10").getBidAmount(), 0.0001);
        assertSame(auction, auctionManager.getAuctionById(10));
    }

    private Auction createRunningAuction(int auctionId, double startingPrice) {
        Item item = new Art(1000 + auctionId, "Painting " + auctionId, "Landscape", startingPrice, "seller-1", "Artist");
        Auction auction = new Auction(
                auctionId,
                item,
                "seller-1",
                LocalDateTime.now().minusHours(1),
                LocalDateTime.now().plusHours(1)
        );
        auction.setStatus(AuctionStatus.RUNNING);
        return auction;
    }

    private Bidder createBidder(int id, double balance) {
        Bidder bidder = new Bidder(id, "user" + id, "user" + id + "@example.com", "secret");
        bidder.getWallet().setBalance(balance);
        userDAO.put(bidder);
        return bidder;
    }

    private static final class InMemoryAuctionDAO extends AuctionDAO {
        private final Map<Integer, Auction> auctions = new HashMap<>();

        @Override
        public void save(Auction auction) {
            auctions.put(auction.getAuctionId(), auction);
        }

        @Override
        public Optional<Auction> findById(int id) {
            return Optional.ofNullable(auctions.get(id));
        }

        @Override
        public void update(Auction auction) {
            auctions.put(auction.getAuctionId(), auction);
        }

        @Override
        public void delete(int id) {
            auctions.remove(id);
        }
    }

    private static final class InMemoryBidTransactionDAO extends BidTransactionDAO {
        private final List<BidTransaction> bids = new ArrayList<>();

        @Override
        public void save(BidTransaction bid) {
            bids.add(bid);
        }

        @Override
        public List<BidTransaction> findByAuctionId(int auctionId) {
            return bids.stream()
                    .filter(bid -> bid.getAuctionId() == auctionId)
                    .sorted(Comparator.comparingDouble(BidTransaction::getBidAmount).reversed())
                    .toList();
        }

        @Override
        public List<BidTransaction> findByBidderId(int bidderId) {
            return bids.stream()
                    .filter(bid -> bid.getBidderId() == bidderId)
                    .sorted(Comparator.comparing(BidTransaction::getBidTime).reversed())
                    .toList();
        }

        @Override
        public BidTransaction findHighestBidByAuctionId(int auctionId) {
            return bids.stream()
                    .filter(bid -> bid.getAuctionId() == auctionId)
                    .max(Comparator.comparingDouble(BidTransaction::getBidAmount))
                    .orElse(null);
        }
    }

    private static final class InMemoryUserDAO extends UserDAO {
        private final Map<Integer, User> users = new HashMap<>();

        void put(User user) {
            users.put(user.getId(), user);
        }

        @Override
        public Optional<User> findById(int id) {
            return Optional.ofNullable(users.get(id));
        }

        @Override
        public void update(User user) {
            users.put(user.getId(), user);
        }
    }
}
