package common.models;

import common.exceptions.AuthenticationException;
import common.models.auction.*;
import common.models.entity.Entity;
import common.models.item.Art;
import common.models.item.Electronics;
import common.models.item.Item;
import common.models.item.Vehicle;
import common.models.user.*;
import common.userfactory.*;
import common.utils.FormatUtils;
import common.utils.JsonUtils;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class ModelTest {

    @Test
    void entityConstructorAndSetters() {
        Entity e = new Entity(5) {};
        assertEquals(5, e.getId());
        assertNotNull(e.getTimeCreated());
        e.setId(10);
        assertEquals(10, e.getId());
    }

    @Test
    void walletDepositWithdrawSetBalance() {
        Wallet w = new Wallet();
        assertEquals(0, w.getBalance());
        assertTrue(w.deposit(100));
        assertEquals(100, w.getBalance());
        assertFalse(w.deposit(-10));
        assertFalse(w.deposit(0));
        assertTrue(w.withdraw(30));
        assertEquals(70, w.getBalance());
        assertFalse(w.withdraw(100));
        assertFalse(w.withdraw(-5));
        w.setBalance(-50);
        assertEquals(0, w.getBalance());
        w.setBalance(200);
        assertEquals(200, w.getBalance());
    }

    @Test
    void userConstructorAndSetters() {
        Bidder u = new Bidder(1, "alice", "a@e", "pw");
        assertEquals("alice", u.getUsername());
        assertEquals("a@e", u.getEmail());
        assertEquals("pw", u.getPassword());
        assertEquals("BIDDER", u.getRole());
        assertEquals(UserStatus.LOGIN, u.getStatus());
        u.setUsername("bob");
        assertEquals("bob", u.getUsername());
        u.setPassword("newpw");
        assertEquals("newpw", u.getPassword());
        u.logout();
        assertEquals(UserStatus.LOGOUT, u.getStatus());
    }

    @Test
    void itemSubclassesGetters() {
        Art art = new Art(1, "Mona Lisa", "desc", 1000, "s1", "Da Vinci");
        assertEquals("Da Vinci", art.getArtist());
        art.setArtist("Picasso");
        assertEquals("Picasso", art.getArtist());
        assertTrue(art.getInfo().contains("Picasso"));
        Electronics elec = new Electronics(2, "Laptop", "desc", 500, "s2", 24);
        assertEquals(24, elec.getWarrantyPeriod());
        elec.setWarrantyPeriod(12);
        assertEquals(12, elec.getWarrantyPeriod());
        assertTrue(elec.getInfo().contains("Thời gian bảo hành"));
        Vehicle vehicle = new Vehicle(3, "Car", "desc", 10000, "s3", 5000);
        assertEquals(5000, vehicle.getMileage());
        vehicle.setMileage(6000);
        assertEquals(6000, vehicle.getMileage());
        assertTrue(vehicle.getInfo().contains("Quãng đường đã đi"));
        vehicle.setName("Car updated");
        vehicle.setDescription("desc updated");
        assertEquals("Car updated", vehicle.getName());
        assertEquals("desc updated", vehicle.getDescription());
        assertEquals(10000, vehicle.getStartingPrice());
        assertEquals("s3", vehicle.getSellerId());
    }

    @Test
    void bidderPlaceBidAndWallet() {
        Bidder bidder = new Bidder(1, "b", "b@e", "p");
        assertNotNull(bidder.getWallet());
        Auction auction = new Auction(1, null, "s1", LocalDateTime.now(), LocalDateTime.now().plusHours(1));
        auction.setStatus(null);
        assertFalse(bidder.placeBid(auction, 100));
        auction.setStatus(AuctionStatus.FINISHED);
        assertFalse(bidder.placeBid(auction, 100));
    }

    @Test
    void bidderPlaceBidInsufficientWallet() {
        Bidder bidder = new Bidder(2, "b2", "b2@e", "p");
        Item item = new Art(10, "Paint", "desc", 50, "s1", "A");
        Auction auction = new Auction(2, item, "s1", LocalDateTime.now().minusMinutes(5), LocalDateTime.now().plusHours(1));
        assertFalse(bidder.placeBid(auction, 100));
    }

    @Test
    void bidderPlaceBidSuccessAndInvalidAmount() {
        Bidder bidder = new Bidder(4, "b4", "b4@e", "p");
        bidder.getWallet().deposit(1000);
        Item item = new Art(11, "Art", "desc", 100, "s1", "Artist");
        Auction auction = new Auction(6, item, "s1", LocalDateTime.now().minusMinutes(1), LocalDateTime.now().plusHours(1));

        assertTrue(bidder.placeBid(auction, 200));
        assertFalse(bidder.placeBid(auction, 150));
    }

    @Test
    void bidderObserverMethods() {
        Bidder b = new Bidder();
        assertDoesNotThrow(() -> b.updateCurrentBid(null));
        assertDoesNotThrow(() -> b.updateAuctionStatus(null));
    }

    @Test
    void sellerAddRemoveItem() {
        Seller seller = new Seller(1, "s", "s@e", "p");
        assertNotNull(seller.getWallet());
        assertNull(seller.createAuction(null));
        Item item = new Art(1, "Paint", "desc", 100, "s1", "A");
        seller.addItem(item);
        assertTrue(seller.removeItem(item));
        assertFalse(seller.removeItem(item));
    }

    @Test
    void sellerAddNullItem() {
        Seller seller = new Seller(2, "s2", "s2@e", "p");
        assertDoesNotThrow(() -> seller.addItem(null));
    }

    @Test
    void sellerObserverMethods() {
        Seller seller = new Seller(3, "s3", "s3@e", "p");
        assertDoesNotThrow(() -> seller.updateCurrentBid(null));
        assertDoesNotThrow(() -> seller.updateAuctionStatus(null));
    }

    @Test
    void sellerDefaultConstructorAndEditItem() {
        Seller seller = new Seller();
        assertDoesNotThrow(seller::editItem);
    }

    @Test
    void auctionLifecycleAndProcessBid() {
        Item item = new Art(1, "Paint", "desc", 100, "s1", "A");
        Auction auction = new Auction(1, item, "s1", LocalDateTime.now().minusMinutes(5), LocalDateTime.now().plusHours(1));
        BidTransaction bid = new BidTransaction(1, 1, 2, 150);
        boolean result = auction.processBid(bid);
        assertTrue(result);
        assertEquals(150, auction.getCurrentHighestBid());
        BidTransaction lowBid = new BidTransaction(2, 1, 3, 120);
        assertFalse(auction.processBid(lowBid));
    }

    @Test
    void auctionStartAndEnd() {
        Item item = new Electronics(2, "Phone", "desc", 200, "s1", 12);
        Auction running = new Auction(3, item, "s1", LocalDateTime.now().minusMinutes(10), LocalDateTime.now().plusHours(1));
        running.startAuction();
        assertEquals(AuctionStatus.RUNNING, running.getStatus());
        Auction finished = new Auction(4, item, "s1", LocalDateTime.now().minusHours(2), LocalDateTime.now().minusMinutes(10));
        finished.setStatus(AuctionStatus.RUNNING);
        finished.endAuction();
        assertEquals(AuctionStatus.FINISHED, finished.getStatus());
        Auction open = new Auction(5, item, "s1", LocalDateTime.now().plusMinutes(10), LocalDateTime.now().plusHours(1));
        open.endAuction();
        assertEquals(AuctionStatus.OPEN, open.getStatus());
    }

    @Test
    void auctionAntiSniping() {
        Auction auction = new Auction(1, null, "s1", LocalDateTime.now(), LocalDateTime.now().plusSeconds(20));
        assertTrue(auction.checkAndExtendForSniping(LocalDateTime.now()));
        assertFalse(auction.checkAndExtendForSniping(null));
        Auction a2 = new Auction(2, null, "s1", LocalDateTime.now(), null);
        assertFalse(a2.checkAndExtendForSniping(LocalDateTime.now()));
        Auction a3 = new Auction(3, null, "s1", LocalDateTime.now(), LocalDateTime.now().minusMinutes(1));
        assertFalse(a3.checkAndExtendForSniping(LocalDateTime.now()));
    }

    @Test
    void auctionIsClosed() {
        Auction a = new Auction();
        a.setStatus(AuctionStatus.FINISHED);
        assertTrue(a.isClosed());
        a.setStatus(AuctionStatus.PAID);
        assertTrue(a.isClosed());
        a.setStatus(AuctionStatus.CANCELED);
        assertTrue(a.isClosed());
        a.setStatus(AuctionStatus.RUNNING);
        assertFalse(a.isClosed());
    }

    @Test
    void auctionGettersAndSetters() {
        Auction a = new Auction();
        a.setId(99);
        assertEquals(99, a.getAuctionId());
        a.setEndTime(LocalDateTime.of(2026, 12, 31, 23, 59));
        assertNotNull(a.getEndTime());
        a.setCurrentHighestBid(500);
        assertEquals(500, a.getCurrentHighestBid());
        a.setCurrentLeaderId(7);
        assertEquals(7, a.getCurrentLeaderId());
        a.setCurrentLeader(null);
        a.setCurrentLeaderId(8);
        assertEquals(8, a.getCurrentLeaderId());
        Item item = new Art(99, "Test", "desc", 100, "s1", "A");
        a.setItem(item);
        assertSame(item, a.getItem());
    }

    @Test
    void bidTransactionGettersAndSetters() {
        BidTransaction b = new BidTransaction();
        b.setId(1);
        b.setAuctionId(2);
        b.setBidderId(3);
        b.setBidAmount(100.5);
        b.setBidTime(LocalDateTime.now());
        b.setBidder(new Bidder(5, "u", "e", "p"));
        assertEquals(1, b.getId());
        assertEquals(2, b.getAuctionId());
        assertEquals(3, b.getBidderId());
        assertEquals(100.5, b.getBidAmount());
        assertNotNull(b.getBidTime());
        assertEquals(5, b.getBidder().getId());
        assertTrue(b.getDetails().contains("100.5"));
        BidTransaction b2 = new BidTransaction(10, 20, 30, 200);
        assertEquals(10, b2.getId());
        assertEquals(20, b2.getAuctionId());
        assertEquals(30, b2.getBidderId());
        assertEquals(200, b2.getBidAmount());
    }

    @Test
    void enumsHaveExpectedValues() {
        assertEquals(3, UserStatus.values().length);
        assertEquals(5, AuctionStatus.values().length);
    }

    @Test
    void adminConstructor() {
        Admin admin = new Admin(1, "admin", "a@e", "p");
        assertEquals("ADMIN", admin.getRole());
    }

    @Test
    void adminCancelAuction() {
        Admin admin = new Admin(1, "admin", "a@e", "p");
        assertDoesNotThrow(() -> admin.cancelAuction("123"));
    }

    @Test
    void adminDefaultConstructorAndUpdateProfile() {
        Admin admin = new Admin();
        admin.setUsername("x");
        admin.setPassword("y");
        admin.setStatus(UserStatus.LOGIN);
        assertDoesNotThrow(admin::updateProfile);
    }

    @Test
    void formatUtils() {
        assertTrue(FormatUtils.formatCurrency(12345).contains("VND"));
        assertTrue(FormatUtils.formatCurrency(0).contains("0"));
        assertTrue(FormatUtils.formatCurrency(-500).contains("-500"));
        assertEquals("-", FormatUtils.formatDateTime(null));
        assertEquals("-", FormatUtils.formatDateTimeWithSeconds(null));
        assertNotNull(FormatUtils.formatDateTime(LocalDateTime.now()));
        assertNotNull(FormatUtils.formatDateTimeWithSeconds(LocalDateTime.now()));
    }

    @Test
    void jsonUtilsRoundTrip() {
        String json = JsonUtils.toJson(Map.of("key", "value"));
        assertTrue(json.contains("key"));
        Map result = JsonUtils.fromJson(json, Map.class);
        assertEquals("value", result.get("key"));
        assertThrows(IllegalArgumentException.class, () -> JsonUtils.fromJson("not-json", Map.class));
    }

    @Test
    void autoBidAgentCalculateProposedBid() {
        AutoBidAgent agent = new AutoBidAgent(1, 1, 1, 100, 10);
        assertEquals(60, agent.calculateProposedBid(50), 0.001);
        assertEquals(-1, agent.calculateProposedBid(95), 0.001);
        assertEquals(100, agent.calculateProposedBid(90), 0.001);
    }

    @Test
    void autoBidAgentCanBid() {
        AutoBidAgent agent = new AutoBidAgent(1, 1, 1, 100, 10);
        assertTrue(agent.canBid(50));
        assertFalse(agent.canBid(95));
        agent.setActive(false);
        assertFalse(agent.canBid(50));
        agent.setActive(true);
        assertTrue(agent.canBid(50));
    }

    @Test
    void autoBidAgentCompareTo() {
        LocalDateTime now = LocalDateTime.now();
        AutoBidAgent high = new AutoBidAgent(1, 1, 1, 200, 10);
        AutoBidAgent low = new AutoBidAgent(2, 2, 1, 100, 10);
        assertTrue(high.compareTo(low) < 0);
        assertTrue(low.compareTo(high) > 0);
        AutoBidAgent early = new AutoBidAgent(3, 3, 1, 100, 10);
        AutoBidAgent late = new AutoBidAgent(4, 4, 1, 100, 10);
        early.setCreatedAt(now.minusMinutes(10));
        late.setCreatedAt(now);
        assertTrue(early.compareTo(late) < 0);
        assertTrue(late.compareTo(early) > 0);
    }

    @Test
    void autoBidAgentGettersSettersAndToString() {
        AutoBidAgent agent = new AutoBidAgent(1, 1, 1, 100, 10);
        assertNotNull(agent.toString());
        assertTrue(agent.toString().contains("agentId=1"));
        assertEquals(1, agent.getAgentId());
        assertEquals(1, agent.getBidderId());
        assertEquals(1, agent.getAuctionId());
        assertEquals(100, agent.getMaxBid(), 0.001);
        assertEquals(10, agent.getIncrement(), 0.001);
        assertTrue(agent.isActive());
        agent.setAgentId(99);
        agent.setBidderId(99);
        agent.setAuctionId(99);
        agent.setMaxBid(999);
        agent.setIncrement(99);
        agent.setCreatedAt(LocalDateTime.now());
        assertEquals(99, agent.getAgentId());
        assertEquals(99, agent.getBidderId());
        assertEquals(99, agent.getAuctionId());
        assertEquals(999, agent.getMaxBid(), 0.001);
        assertEquals(99, agent.getIncrement(), 0.001);
        assertNotNull(agent.getCreatedAt());
    }

    @Test
    void autoBidAgentDefaultCompareWhenCreatedAtMissing() {
        AutoBidAgent a = new AutoBidAgent();
        AutoBidAgent b = new AutoBidAgent();
        assertEquals(0, a.compareTo(b));
    }

    @Test
    void userFactorySingleton() {
        assertSame(UserFactory.getInstance(), UserFactory.getInstance());
    }

    @Test
    void userFactoryCreateValidTypes() {
        new BidderCreator();
        new SellerCreator();
        new AdminCreator();
        User bidder = UserFactory.createUser("bidder", 1, "b", "b@e", "p");
        assertInstanceOf(Bidder.class, bidder);
        User seller = UserFactory.createUser("seller", 2, "s", "s@e", "p");
        assertInstanceOf(Seller.class, seller);
        User admin = UserFactory.createUser("admin", 3, "a", "a@e", "p");
        assertInstanceOf(Admin.class, admin);
    }

    @Test
    void userFactoryCaseInsensitive() {
        new BidderCreator();
        assertDoesNotThrow(() -> UserFactory.createUser("BIDDER", 1, "b", "b@e", "p"));
        assertDoesNotThrow(() -> UserFactory.createUser("Bidder", 1, "b", "b@e", "p"));
    }

    @Test
    void userFactoryUnsupportedTypeThrows() {
        assertThrows(AuthenticationException.class,
                () -> UserFactory.createUser("unknown", 1, "x", "x@e", "p"));
    }

    @Test
    void itemGetClassSimpleName() {
        Art art = new Art(1, "a", "d", 1, "s1", "artist");
        assertEquals("Art", art.getClass_SimpleName());
        Electronics elec = new Electronics(2, "b", "d", 1, "s1", 0);
        assertEquals("Electronics", elec.getClass_SimpleName());
        Vehicle vehicle = new Vehicle(3, "c", "d", 1, "s1", 0);
        assertEquals("Vehicle", vehicle.getClass_SimpleName());
    }

    @Test
    void userSetStatusGetStatus() {
        Bidder u = new Bidder(1, "u", "u@e", "p");
        u.setStatus(UserStatus.LOGOUT);
        assertEquals(UserStatus.LOGOUT, u.getStatus());
        u.setStatus(UserStatus.LOGIN);
        assertEquals(UserStatus.LOGIN, u.getStatus());
        u.setStatus(UserStatus.BANNED);
        assertEquals(UserStatus.BANNED, u.getStatus());
    }

    @Test
    void userEmptyConstructor() {
        Bidder b = new Bidder();
        assertNull(b.getUsername());
        assertNull(b.getEmail());
        assertNull(b.getPassword());
        assertNull(b.getRole());
        assertNull(b.getStatus());
    }

    @Test
    void auctionCurrentLeaderIdUsesLeaderObjectWhenPresent() {
        Auction auction = new Auction();
        Bidder leader = new Bidder(77, "u77", "u77@e", "p");
        auction.setCurrentLeader(leader);
        auction.setCurrentLeaderId(10);
        assertEquals(77, auction.getCurrentLeaderId());
    }
}
