package server.repository;

import common.models.auction.Auction;
import common.models.auction.AuctionStatus;
import common.models.auction.BidTransaction;
import common.models.item.Art;
import common.models.item.Electronics;
import common.models.item.Item;
import common.models.item.Vehicle;
import common.models.user.Admin;
import common.models.user.Bidder;
import common.models.user.Seller;
import common.models.user.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class DaoTest {
    private static final String DB_URL_KEY = "db.url";
    private static final String DB_USER_KEY = "db.user";
    private static final String DB_PASSWORD_KEY = "db.password";

    private String oldUrl;
    private String oldUser;
    private String oldPassword;

    @BeforeEach
    void setUp() {
        oldUrl = System.getProperty(DB_URL_KEY);
        oldUser = System.getProperty(DB_USER_KEY);
        oldPassword = System.getProperty(DB_PASSWORD_KEY);

        // Force deterministic DB connection failure regardless local MySQL state.
        System.setProperty(DB_URL_KEY, "jdbc:mysql://127.0.0.1:1/auction_db?connectTimeout=100&socketTimeout=100");
        System.setProperty(DB_USER_KEY, "root");
        System.setProperty(DB_PASSWORD_KEY, "");
    }

    @AfterEach
    void tearDown() {
        restore(DB_URL_KEY, oldUrl);
        restore(DB_USER_KEY, oldUser);
        restore(DB_PASSWORD_KEY, oldPassword);
    }

    @Test
    void itemDaoMethodsThrowRuntimeWhenDatabaseUnavailable() {
        ItemDAO dao = new ItemDAO();
        Item item = new Electronics(1, "Laptop", "Desc", 1000, "seller-1", 12);

        assertThrows(RuntimeException.class, () -> dao.save(item));
        assertThrows(RuntimeException.class, () -> dao.findById(1));
        assertThrows(RuntimeException.class, dao::findAll);
        assertThrows(RuntimeException.class, () -> dao.findBySellerId("seller-1"));
        assertThrows(RuntimeException.class, () -> dao.findByNameContaining("Lap"));
        assertThrows(RuntimeException.class, () -> dao.update(item));
        assertThrows(RuntimeException.class, () -> dao.delete(1));
    }

    @Test
    void auctionDaoMethodsThrowRuntimeWhenDatabaseUnavailable() {
        AuctionDAO dao = new AuctionDAO();
        Item item = new Electronics(2, "Phone", "Desc", 500, "seller-2", 12);
        Auction auction = new Auction(
                2,
                item,
                "seller-2",
                LocalDateTime.now().plusMinutes(1),
                LocalDateTime.now().plusMinutes(10));
        auction.setStatus(AuctionStatus.OPEN);

        assertThrows(RuntimeException.class, () -> dao.save(auction));
        assertThrows(RuntimeException.class, () -> dao.findById(2));
        assertThrows(RuntimeException.class, dao::findAll);
        assertThrows(RuntimeException.class, () -> dao.findByStatus(AuctionStatus.RUNNING));
        assertThrows(RuntimeException.class, () -> dao.findBySellerId("seller-2"));
        assertThrows(RuntimeException.class, dao::findExpiredButNotClosed);
        assertThrows(RuntimeException.class, () -> dao.update(auction));
        assertThrows(RuntimeException.class, () -> dao.delete(2));
    }

    @Test
    void bidTransactionDaoMethodsThrowRuntimeWhenDatabaseUnavailableOrUnsupported() {
        BidTransactionDAO dao = new BidTransactionDAO();
        BidTransaction bid = new BidTransaction(0, 3, 4, 700);
        bid.setBidTime(LocalDateTime.now());

        assertThrows(RuntimeException.class, () -> dao.save(bid));
        assertThrows(RuntimeException.class, () -> dao.findById(1));
        assertThrows(RuntimeException.class, dao::findAll);
        assertThrows(RuntimeException.class, () -> dao.findByAuctionId(3));
        assertThrows(RuntimeException.class, () -> dao.findByBidderId(4));
        assertThrows(RuntimeException.class, () -> dao.findHighestBidByAuctionId(3));
        assertThrows(UnsupportedOperationException.class, () -> dao.update(bid));
        assertThrows(UnsupportedOperationException.class, () -> dao.delete(1));
    }

    @Test
    void userDaoMethodsThrowRuntimeWhenDatabaseUnavailable() {
        UserDAO dao = new UserDAO();
        User user = new Bidder(5, "alice", "alice@example.com", "secret");

        assertThrows(RuntimeException.class, () -> dao.save(user));
        assertThrows(RuntimeException.class, () -> dao.findById(5));
        assertThrows(RuntimeException.class, dao::findAll);
        assertThrows(RuntimeException.class, () -> dao.update(user));
        assertThrows(RuntimeException.class, () -> dao.updateRoleAndStatus(user, "BIDDER", "LOGIN"));
        assertThrows(RuntimeException.class, () -> dao.delete(5));
        assertThrows(RuntimeException.class, () -> dao.findByUsername("alice"));
        assertThrows(RuntimeException.class, () -> dao.findByEmail("alice@example.com"));
        assertThrows(RuntimeException.class, () -> dao.existsByUsername("alice"));
        assertThrows(RuntimeException.class, () -> dao.existsByEmail("alice@example.com"));
    }

    @Test
    void itemDaoMapToItemCoversAllSupportedTypesAndInvalid() {
        ItemDAO dao = new ItemDAO();

        Item electronics = invokePrivate(
                dao,
                "mapToItem",
                fakeResultSet(mapOf(
                        "id", 1, "name", "Laptop", "description", "D", "starting_price", 100.0,
                        "seller_id", "s1", "item_type", "ELECTRONICS", "warranty_period", 24)));
        assertInstanceOf(Electronics.class, electronics);

        Item vehicle = invokePrivate(
                dao,
                "mapToItem",
                fakeResultSet(mapOf(
                        "id", 2, "name", "Car", "description", "D", "starting_price", 200.0,
                        "seller_id", "s2", "item_type", "VEHICLE", "mileage", 5000)));
        assertInstanceOf(Vehicle.class, vehicle);

        Item art = invokePrivate(
                dao,
                "mapToItem",
                fakeResultSet(mapOf(
                        "id", 3, "name", "Paint", "description", "D", "starting_price", 300.0,
                        "seller_id", "s3", "item_type", "ART", "artist", "A")));
        assertInstanceOf(Art.class, art);

        assertThrows(
                RuntimeException.class,
                () -> invokePrivate(
                        dao,
                        "mapToItem",
                        fakeResultSet(mapOf(
                                "id", 4, "name", "X", "description", "D", "starting_price", 1.0,
                                "seller_id", "s4", "item_type", "UNKNOWN"))));
    }

    @Test
    void auctionDaoMapHelpersCoverNullLeaderAndInvalidType() {
        AuctionDAO dao = new AuctionDAO();
        ResultSet rs = fakeResultSet(mapOf(
                "id", 10,
                "seller_id", "seller-1",
                "start_time", Timestamp.valueOf(LocalDateTime.now().minusHours(1)),
                "end_time", Timestamp.valueOf(LocalDateTime.now().plusHours(1)),
                "current_highest_bid", 150.0,
                "current_leader_id", null,
                "status", "RUNNING",
                "item_id", 100,
                "item_name", "Phone",
                "description", "Desc",
                "starting_price", 100.0,
                "item_seller_id", "seller-1",
                "item_type", "ELECTRONICS",
                "warranty_period", 12));

        Auction auction = invokePrivate(dao, "mapToAuction", rs);
        assertNotNull(auction);
        assertNull(auction.getCurrentLeaderId());
        assertEquals(AuctionStatus.RUNNING, auction.getStatus());

        Integer fromNumber = invokePrivate(
                dao, "readNullableInteger", fakeResultSet(mapOf("current_leader_id", 99)), "current_leader_id");
        assertEquals(99, fromNumber);

        Integer fromString = invokePrivate(
                dao, "readNullableInteger", fakeResultSet(mapOf("current_leader_id", "77")), "current_leader_id");
        assertEquals(77, fromString);

        Integer fromEmpty = invokePrivate(
                dao, "readNullableInteger", fakeResultSet(mapOf("current_leader_id", "  ")), "current_leader_id");
        assertNull(fromEmpty);

        assertThrows(
                RuntimeException.class,
                () -> invokePrivate(
                        dao,
                        "mapToItem",
                        fakeResultSet(mapOf(
                                "item_id", 1, "item_name", "X", "description", "D",
                                "starting_price", 1.0, "item_seller_id", "s", "item_type", "BAD"))));
    }

    @Test
    void bidTransactionDaoMapMethodBuildsEntity() {
        BidTransactionDAO dao = new BidTransactionDAO();
        LocalDateTime time = LocalDateTime.now();
        BidTransaction bid = invokePrivate(
                dao,
                "mapToBidTransaction",
                fakeResultSet(mapOf(
                        "id", 11,
                        "auction_id", 22,
                        "bidder_id", 33,
                        "bid_amount", 444.5,
                        "bid_time", Timestamp.valueOf(time))));

        assertEquals(11, bid.getId());
        assertEquals(22, bid.getAuctionId());
        assertEquals(33, bid.getBidderId());
        assertEquals(444.5, bid.getBidAmount());
        assertEquals(time, bid.getBidTime());
    }

    @Test
    void userDaoMapAndWalletHelpersCoverRolesAndFallback() {
        UserDAO dao = new UserDAO();

        User bidder = invokePrivate(
                dao,
                "mapToUser",
                fakeResultSet(mapOf(
                        "id", 1, "username", "b", "email", "b@e", "password", "p",
                        "role", "BIDDER", "status", "LOGIN", "wallet_balance", 10.5)));
        assertInstanceOf(Bidder.class, bidder);
        assertEquals(10.5, ((Bidder) bidder).getWallet().getBalance());

        User seller = invokePrivate(
                dao,
                "mapToUser",
                fakeResultSet(mapOf(
                        "id", 2, "username", "s", "email", "s@e", "password", "p",
                        "role", "SELLER", "status", "LOGOUT", "wallet_balance", 20.5)));
        assertInstanceOf(Seller.class, seller);
        assertEquals(20.5, ((Seller) seller).getWallet().getBalance());

        User admin = invokePrivate(
                dao,
                "mapToUser",
                fakeResultSet(mapOf(
                        "id", 3, "username", "a", "email", "a@e", "password", "p",
                        "role", "ADMIN", "status", "LOGIN")));
        assertInstanceOf(Admin.class, admin);

        User bidderWithMissingWallet = invokePrivate(
                dao,
                "mapToUser",
                fakeResultSet(
                        mapOf(
                                "id", 4, "username", "bw", "email", "bw@e", "password", "p",
                                "role", "BIDDER", "status", "LOGIN"),
                        "wallet_balance"));
        assertInstanceOf(Bidder.class, bidderWithMissingWallet);
        assertEquals(0.0, ((Bidder) bidderWithMissingWallet).getWallet().getBalance());

        assertThrows(
                RuntimeException.class,
                () -> invokePrivate(
                        dao,
                        "mapToUser",
                        fakeResultSet(mapOf(
                                "id", 5, "username", "x", "email", "x@e", "password", "p",
                                "role", "NOPE", "status", "LOGIN"))));
    }

    @Test
    void userDaoGetWalletBalanceHelperCoversAllBranches() {
        UserDAO dao = new UserDAO();
        Bidder bidder = new Bidder(10, "b", "b@e", "p");
        bidder.getWallet().setBalance(11.0);
        Seller seller = new Seller(11, "s", "s@e", "p");
        seller.getWallet().setBalance(22.0);
        Admin admin = new Admin(12, "a", "a@e", "p");

        double b = invokePrivate(dao, "getWalletBalance", bidder);
        double s = invokePrivate(dao, "getWalletBalance", seller);
        double a = invokePrivate(dao, "getWalletBalance", admin);

        assertEquals(11.0, b);
        assertEquals(22.0, s);
        assertEquals(0.0, a);
    }

    private void restore(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T invokePrivate(Object target, String methodName, Object... args) {
        try {
            Method method = findMethod(target.getClass(), methodName, args);
            method.setAccessible(true);
            return (T) method.invoke(target, args);
        } catch (Exception e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            if (cause != null) {
                throw new RuntimeException(cause);
            }
            throw new RuntimeException(e);
        }
    }

    private Method findMethod(Class<?> type, String methodName, Object[] args) throws NoSuchMethodException {
        for (Method method : type.getDeclaredMethods()) {
            if (!method.getName().equals(methodName)) {
                continue;
            }
            Class<?>[] params = method.getParameterTypes();
            if (params.length != args.length) {
                continue;
            }
            boolean matched = true;
            for (int i = 0; i < params.length; i++) {
                Object arg = args[i];
                if (arg == null) {
                    continue;
                }
                if (!params[i].isAssignableFrom(arg.getClass())) {
                    matched = false;
                    break;
                }
            }
            if (matched) {
                return method;
            }
        }
        throw new NoSuchMethodException(methodName);
    }

    private ResultSet fakeResultSet(Map<String, Object> values) {
        return fakeResultSet(values, null);
    }

    private ResultSet fakeResultSet(Map<String, Object> values, String throwOnGetDoubleColumn) {
        InvocationHandler handler = (proxy, method, args) -> {
            String name = method.getName();
            if ("getInt".equals(name)) {
                Object v = values.get((String) args[0]);
                if (v == null) {
                    return 0;
                }
                if (v instanceof Number number) {
                    return number.intValue();
                }
                return Integer.parseInt(v.toString());
            }
            if ("getDouble".equals(name)) {
                String col = (String) args[0];
                if (throwOnGetDoubleColumn != null && throwOnGetDoubleColumn.equals(col)) {
                    throw new SQLException("forced");
                }
                Object v = values.get(col);
                if (v == null) {
                    return 0.0;
                }
                if (v instanceof Number number) {
                    return number.doubleValue();
                }
                return Double.parseDouble(v.toString());
            }
            if ("getString".equals(name)) {
                Object v = values.get((String) args[0]);
                return v == null ? null : v.toString();
            }
            if ("getObject".equals(name)) {
                return values.get((String) args[0]);
            }
            if ("getTimestamp".equals(name)) {
                Object v = values.get((String) args[0]);
                if (v == null) {
                    return null;
                }
                if (v instanceof Timestamp ts) {
                    return ts;
                }
                if (v instanceof LocalDateTime time) {
                    return Timestamp.valueOf(time);
                }
                return null;
            }
            if ("next".equals(name)) {
                return false;
            }
            if ("close".equals(name)) {
                return null;
            }
            Class<?> returnType = method.getReturnType();
            if (returnType == boolean.class) {
                return false;
            }
            if (returnType == int.class) {
                return 0;
            }
            if (returnType == long.class) {
                return 0L;
            }
            if (returnType == double.class) {
                return 0.0;
            }
            return null;
        };
        return (ResultSet) Proxy.newProxyInstance(
                DaoTest.class.getClassLoader(),
                new Class<?>[] { ResultSet.class },
                handler);
    }

    private Map<String, Object> mapOf(Object... keyValues) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }
}
