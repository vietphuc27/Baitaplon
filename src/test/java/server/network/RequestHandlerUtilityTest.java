package server.network;

import common.models.auction.Auction;
import common.models.item.Art;
import common.models.item.Electronics;
import common.models.item.Item;
import common.models.item.Vehicle;
import common.models.auction.BidTransaction;
import common.models.user.Bidder;
import common.models.user.Seller;
import common.models.user.User;
import common.models.user.UserStatus;
import common.utils.JsonUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import server.manager.AutoBidManager;
import server.manager.ConnectionManager;
import server.service.AuthService;
import server.service.BidService;
import server.service.AuctionService;
import server.service.ItemService;
import server.service.UserService;

import java.net.Socket;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class RequestHandlerUtilityTest {

    @AfterEach
    public void tearDown() {
        ConnectionManager.getInstance().disconnectAll();
    }

    @Test
    public void handleSupportsPingLogoutAndErrorFlows() {
        RequestHandler handler = new RequestHandler();

        Map<?, ?> ping = JsonUtils.fromJson(handler.handle("{\"action\":\"ping\"}", null), Map.class);
        assertEquals("success", ping.get("status"));
        assertEquals("pong", ping.get("message"));

        Map<?, ?> logout = JsonUtils.fromJson(handler.handle("{\"action\":\"logout\"}", null), Map.class);
        assertEquals("success", logout.get("status"));

        Map<?, ?> unsupported = JsonUtils.fromJson(handler.handle("{\"action\":\"nope\"}", null), Map.class);
        assertEquals("error", unsupported.get("status"));
        assertTrue(String.valueOf(unsupported.get("message")).contains("ho tro"));

        Map<?, ?> missingAction = JsonUtils.fromJson(handler.handle("{\"x\":1}", null), Map.class);
        assertEquals("error", missingAction.get("status"));

        Map<?, ?> invalidJson = JsonUtils.fromJson(handler.handle("not-json", null), Map.class);
        assertEquals("error", invalidJson.get("status"));
        assertTrue(String.valueOf(invalidJson.get("message")).contains("Invalid JSON format"));
    }

    @Test
    public void normalizeTypeAndRequiredParsingHelpersCoverBranches() {
        RequestHandler handler = new RequestHandler();

        assertEquals("art", invoke(handler, "normalizeItemType", new Class<?>[]{String.class}, "nghe thuat"));
        assertEquals("electronics", invoke(handler, "normalizeItemType", new Class<?>[]{String.class}, "điện tử"));
        assertEquals("vehicle", invoke(handler, "normalizeItemType", new Class<?>[]{String.class}, "phương tiện"));
        assertEquals("other", invoke(handler, "normalizeItemType", new Class<?>[]{String.class}, "other"));

        Map<String, Object> req = new HashMap<>();
        req.put("text", " abc ");
        req.put("intText", "123");
        req.put("intNum", 9);
        req.put("doubleText", "45.6");
        req.put("doubleNum", 7.5);
        req.put("blank", "   ");

        assertEquals("abc", invoke(handler, "getRequiredText", new Class<?>[]{Map.class, String.class}, req, "text"));
        assertEquals(123, invoke(handler, "getRequiredInt", new Class<?>[]{Map.class, String.class}, req, "intText"));
        assertEquals(9, invoke(handler, "getRequiredInt", new Class<?>[]{Map.class, String.class}, req, "intNum"));
        assertEquals(45.6, (double) invoke(handler, "getRequiredDouble", new Class<?>[]{Map.class, String.class}, req, "doubleText"), 0.0001);
        assertEquals(7.5, (double) invoke(handler, "getRequiredDouble", new Class<?>[]{Map.class, String.class}, req, "doubleNum"), 0.0001);

        assertNull(invoke(handler, "getOptionalText", new Class<?>[]{Map.class, String.class}, req, "missing"));
        assertNull(invoke(handler, "getOptionalText", new Class<?>[]{Map.class, String.class}, req, "blank"));

        assertThrows(IllegalArgumentException.class,
                () -> invoke(handler, "getRequiredText", new Class<?>[]{Map.class, String.class}, req, "missing"));
        assertThrows(IllegalArgumentException.class,
                () -> invoke(handler, "getRequiredInt", new Class<?>[]{Map.class, String.class}, Map.of("a", "x"), "a"));
        assertThrows(IllegalArgumentException.class,
                () -> invoke(handler, "getRequiredDouble", new Class<?>[]{Map.class, String.class}, Map.of("a", "x"), "a"));
    }

    @Test
    public void buildItemAndAuctionMappingCoverTypeBranches() {
        RequestHandler handler = new RequestHandler();

        Map<String, Object> artReq = Map.of("artist", "Artist A");
        Object art = invoke(handler, "buildItem",
                new Class<?>[]{int.class, String.class, String.class, String.class, double.class, String.class, Map.class},
                1, "seller-1", "Painting", "desc", 100.0, "art", artReq);
        assertInstanceOf(Art.class, art);

        Object electronics = invoke(handler, "buildItem",
                new Class<?>[]{int.class, String.class, String.class, String.class, double.class, String.class, Map.class},
                2, "seller-1", "Laptop", "desc", 100.0, "điện tử", Map.of());
        assertInstanceOf(Electronics.class, electronics);

        Object vehicle = invoke(handler, "buildItem",
                new Class<?>[]{int.class, String.class, String.class, String.class, double.class, String.class, Map.class},
                3, "seller-1", "Car", "desc", 100.0, "vehicle", Map.of("mileage", "25000"));
        assertInstanceOf(Vehicle.class, vehicle);
        assertEquals(25000, ((Vehicle) vehicle).getMileage());

        Object defaultMileageVehicle = invoke(handler, "buildItem",
                new Class<?>[]{int.class, String.class, String.class, String.class, double.class, String.class, Map.class},
                4, "seller-1", "Bike", "desc", 100.0, "phuong tien", Map.of());
        assertEquals(0, ((Vehicle) defaultMileageVehicle).getMileage());

        assertThrows(IllegalArgumentException.class, () -> invoke(handler, "buildItem",
                new Class<?>[]{int.class, String.class, String.class, String.class, double.class, String.class, Map.class},
                5, "seller-1", "Painting", "desc", 100.0, "art", Map.of()));

        assertThrows(NumberFormatException.class, () -> invoke(handler, "buildItem",
                new Class<?>[]{int.class, String.class, String.class, String.class, double.class, String.class, Map.class},
                6, "seller-1", "Car", "desc", 100.0, "vehicle", Map.of("mileage", "bad-number")));

        assertThrows(IllegalArgumentException.class, () -> invoke(handler, "buildItem",
                new Class<?>[]{int.class, String.class, String.class, String.class, double.class, String.class, Map.class},
                7, "seller-1", "Thing", "desc", 100.0, "unknown-type", Map.of()));

        Auction auction = new Auction(
                20,
                (Item) electronics,
                "seller-20",
                LocalDateTime.now().minusHours(1),
                LocalDateTime.now().plusHours(2));
        auction.setCurrentHighestBid(999.0);
        auction.setCurrentLeaderId(88);

        Map<?, ?> auctionMap = (Map<?, ?>) invoke(handler, "toAuctionMap", new Class<?>[]{Auction.class}, auction);
        assertEquals("20", auctionMap.get("auctionId"));
        assertEquals("Điện tử", auctionMap.get("itemType"));
        assertEquals(999.0, auctionMap.get("currentPrice"));

        assertEquals("Tác phẩm nghệ thuật", invoke(handler, "toItemTypeDisplay", new Class<?>[]{Item.class}, new Art(1, "a", "d", 1, "s", "ar")));
        assertEquals("Điện tử", invoke(handler, "toItemTypeDisplay", new Class<?>[]{Item.class}, new Electronics(1, "a", "d", 1, "s", 0)));
        assertEquals("Phương tiện", invoke(handler, "toItemTypeDisplay", new Class<?>[]{Item.class}, new Vehicle(1, "a", "d", 1, "s", 0)));

        Item custom = new Item(1, "x", "d", 1, "s") {
            @Override
            public String getInfo() {
                return "x";
            }
        };
        assertEquals(custom.getClass_SimpleName(), invoke(handler, "toItemTypeDisplay", new Class<?>[]{Item.class}, custom));
    }

    @Test
    public void buildUserResponseIncludesTokenAndWalletByRole() {
        RequestHandler handler = new RequestHandler();

        Bidder bidder = new Bidder(10, "bid", "b@e", "pw");
        bidder.setStatus(UserStatus.LOGIN);
        bidder.getWallet().deposit(500.0);

        Map<?, ?> bidderResponse = JsonUtils.fromJson(
                (String) invoke(handler, "buildUserResponse", new Class<?>[]{common.models.user.User.class, String.class}, bidder, "token-1"),
                Map.class);
        assertEquals("success", bidderResponse.get("status"));
        assertEquals("token-1", bidderResponse.get("token"));
        assertEquals(500.0, ((Number) bidderResponse.get("walletBalance")).doubleValue(), 0.0001);

        Seller seller = new Seller(11, "sell", "s@e", "pw");
        seller.setStatus(UserStatus.LOGOUT);
        seller.getWallet().deposit(250.0);

        Map<?, ?> sellerResponse = JsonUtils.fromJson(
                (String) invoke(handler, "buildUserResponse", new Class<?>[]{common.models.user.User.class, String.class}, seller, "  "),
                Map.class);
        assertEquals("success", sellerResponse.get("status"));
        assertNull(sellerResponse.get("token"));
        assertEquals(250.0, ((Number) sellerResponse.get("walletBalance")).doubleValue(), 0.0001);
    }

    @Test
    public void handleCoversActionCasesWithInjectedServices() throws Exception {
        RequestHandler handler = new RequestHandler();

        Bidder loginUser = new Bidder(100, "login-user", "l@e", "pw");
        loginUser.setStatus(UserStatus.LOGIN);
        loginUser.getWallet().deposit(600);

        Bidder bidder = new Bidder(101, "bidder", "b@e", "pw");
        bidder.setStatus(UserStatus.LOGOUT);
        bidder.getWallet().deposit(1000);
        Seller seller = new Seller(102, "seller", "s@e", "pw");
        seller.setStatus(UserStatus.LOGIN);
        User admin = new User(103, "admin", "a@e", "pw", "ADMIN") {};

        FakeAuthService authService = new FakeAuthService(loginUser);
        FakeUserService userService = new FakeUserService(List.of(bidder, seller, admin));
        FakeBidService bidService = new FakeBidService();

        inject(handler, "authService", authService);
        inject(handler, "userService", userService);
        inject(handler, "bidService", bidService);

        ClientHandler clientHandler = new ClientHandler(new Socket(), handler, ConnectionManager.getInstance());

        Map<?, ?> login = asMap(handler.handle(
                "{\"action\":\"login\",\"username\":\"u\",\"password\":\"p\"}",
                clientHandler));
        assertEquals("success", login.get("status"));
        assertEquals("token-fixed", login.get("accessToken"));
        assertEquals("refresh-fixed", login.get("refreshToken"));
        assertTrue(clientHandler.isAuthenticated());

        AutoBidManager.getInstance().resetForTesting();
        AutoBidManager.getInstance().registerAgent(loginUser.getId(), 10, 200, 10);
        assertTrue(AutoBidManager.getInstance().hasActiveAgent(loginUser.getId(), 10));

        Map<?, ?> logout = asMap(handler.handle("{\"action\":\"logout\"}", clientHandler));
        assertEquals("success", logout.get("status"));
        assertEquals("token-fixed", authService.lastLogoutToken);
        assertFalse(clientHandler.isAuthenticated());
        assertFalse(AutoBidManager.getInstance().hasActiveAgent(loginUser.getId(), 10));

        Map<?, ?> register = asMap(handler.handle(
                "{\"action\":\"register\",\"username\":\"new\",\"email\":\"n@e\",\"password\":\"pw\",\"role\":\"bidder\"}",
                null));
        assertEquals("success", register.get("status"));

        Map<?, ?> allUsers = asMap(handler.handle("{\"action\":\"get_all_users\"}", null));
        assertEquals("success", allUsers.get("status"));
        assertEquals(3, ((List<?>) allUsers.get("users")).size());

        assertEquals("success", asMap(handler.handle("{\"action\":\"ban_user\",\"userId\":101}", null)).get("status"));
        assertEquals("success", asMap(handler.handle("{\"action\":\"unban_user\",\"userId\":101}", null)).get("status"));

        Map<?, ?> switchRole = asMap(handler.handle(
                "{\"action\":\"switch_role\",\"userId\":102,\"targetRole\":\"BIDDER\"}",
                null));
        assertEquals("success", switchRole.get("status"));
        assertEquals("BIDDER", switchRole.get("role"));

        Map<?, ?> placeBidError = asMap(handler.handle(
                "{\"action\":\"place_bid\",\"auctionId\":\"10\",\"bidderId\":102,\"amount\":200}",
                null));
        assertEquals("error", placeBidError.get("status"));

        Map<?, ?> bidderHistory = asMap(handler.handle(
                "{\"action\":\"get_bidder_bid_history\",\"bidderId\":\"101\"}",
                null));
        assertEquals("success", bidderHistory.get("status"));
        assertEquals(1, ((List<?>) bidderHistory.get("bids")).size());

        Map<?, ?> auctionHistory = asMap(handler.handle(
                "{\"action\":\"get_auction_bid_history\",\"auctionId\":\"10\"}",
                null));
        assertEquals("success", auctionHistory.get("status"));
        assertEquals(1, ((List<?>) auctionHistory.get("bids")).size());

        Map<?, ?> userById = asMap(handler.handle(
                "{\"action\":\"get_user_by_id\",\"userId\":101}",
                null));
        assertEquals("success", userById.get("status"));
        assertEquals(101.0, userById.get("userId"));
        assertNotNull(userById.get("walletBalance"));

        Map<?, ?> missingUser = asMap(handler.handle(
                "{\"action\":\"get_user_by_id\",\"userId\":99999}",
                null));
        assertEquals("error", missingUser.get("status"));

        Map<?, ?> updateWalletError = asMap(handler.handle(
                "{\"action\":\"update_wallet_deposit\",\"userId\":103,\"amount\":100}",
                null));
        assertEquals("error", updateWalletError.get("status"));

        Map<?, ?> registerAutoBid = asMap(handler.handle(
                "{\"action\":\"register_auto_bid\",\"bidderId\":101,\"auctionId\":10,\"maxBid\":500,\"increment\":10}",
                null));
        assertEquals("success", registerAutoBid.get("status"));
        assertEquals(777.0, registerAutoBid.get("agentId"));

        AutoBidManager.getInstance().resetForTesting();
        int agentId = AutoBidManager.getInstance().registerAgent(101, 10, 200, 10);
        Map<?, ?> cancelOk = asMap(handler.handle(
                "{\"action\":\"cancel_auto_bid\",\"agentId\":" + agentId + "}",
                null));
        assertEquals("success", cancelOk.get("status"));

        Map<?, ?> cancelMissing = asMap(handler.handle(
                "{\"action\":\"cancel_auto_bid\",\"agentId\":9999}",
                null));
        assertEquals("error", cancelMissing.get("status"));

        AutoBidManager.getInstance().resetForTesting();
        Map<?, ?> inactiveStatus = asMap(handler.handle(
                "{\"action\":\"get_auto_bid_status\",\"bidderId\":101,\"auctionId\":10}",
                null));
        assertEquals(false, inactiveStatus.get("active"));

        int activeId = AutoBidManager.getInstance().registerAgent(101, 10, 300, 20);
        Map<?, ?> activeStatus = asMap(handler.handle(
                "{\"action\":\"get_auto_bid_status\",\"bidderId\":101,\"auctionId\":10}",
                null));
        assertEquals("success", activeStatus.get("status"));
        assertEquals(true, activeStatus.get("active"));
        assertEquals((double) activeId, activeStatus.get("agentId"));
    }

    @Test
    public void handleCreateAuctionCoversSuccessAndValidationBranches() throws Exception {
        RequestHandler handler = new RequestHandler();

        FakeItemService itemService = new FakeItemService();
        FakeAuctionService auctionService = new FakeAuctionService(itemService);
        inject(handler, "itemService", itemService);
        inject(handler, "auctionService", auctionService);

        String successRequest = "{"
                + "\"action\":\"create_auction\","
                + "\"sellerId\":\"s-1\","
                + "\"itemName\":\"Mona\","
                + "\"itemType\":\"art\","
                + "\"startPrice\":1000,"
                + "\"description\":\"desc\","
                + "\"artist\":\"Artist A\","
                + "\"startTime\":\"" + LocalDateTime.now().plusMinutes(5) + "\","
                + "\"endTime\":\"" + LocalDateTime.now().plusHours(2) + "\""
                + "}";
        Map<?, ?> success = asMap(handler.handle(successRequest, null));
        assertEquals("success", success.get("status"));
        assertEquals(909.0, success.get("auctionId"));
        assertEquals(1, itemService.savedItems.size());
        assertInstanceOf(Art.class, itemService.savedItems.getFirst());

        String missingArtistRequest = "{"
                + "\"action\":\"create_auction\","
                + "\"sellerId\":\"s-1\","
                + "\"itemName\":\"Mona\","
                + "\"itemType\":\"art\","
                + "\"startPrice\":1000,"
                + "\"description\":\"desc\","
                + "\"startTime\":\"" + LocalDateTime.now().plusMinutes(5) + "\","
                + "\"endTime\":\"" + LocalDateTime.now().plusHours(2) + "\""
                + "}";
        Map<?, ?> missingArtist = asMap(handler.handle(missingArtistRequest, null));
        assertEquals("error", missingArtist.get("status"));

        String invalidTypeRequest = "{"
                + "\"action\":\"create_auction\","
                + "\"sellerId\":\"s-1\","
                + "\"itemName\":\"Thing\","
                + "\"itemType\":\"unknown\","
                + "\"startPrice\":1000,"
                + "\"description\":\"desc\","
                + "\"startTime\":\"" + LocalDateTime.now().plusMinutes(5) + "\","
                + "\"endTime\":\"" + LocalDateTime.now().plusHours(2) + "\""
                + "}";
        Map<?, ?> invalidType = asMap(handler.handle(invalidTypeRequest, null));
        assertEquals("error", invalidType.get("status"));
    }

    @Test
    public void handleCreateAuctionBroadcastsCreatedPush() throws Exception {
        ConnectionManager manager = ConnectionManager.getInstance();
        manager.disconnectAll();

        RequestHandler handler = new RequestHandler();
        FakeItemService itemService = new FakeItemService();
        FakeAuctionService auctionService = new FakeAuctionService(itemService);
        CapturingClientHandler clientHandler = new CapturingClientHandler("listener-1");
        manager.addClient(clientHandler);

        inject(handler, "itemService", itemService);
        inject(handler, "auctionService", auctionService);

        String request = "{"
                + "\"action\":\"create_auction\","
                + "\"sellerId\":\"s-1\","
                + "\"itemName\":\"Laptop\","
                + "\"itemType\":\"electronics\","
                + "\"startPrice\":1000,"
                + "\"description\":\"desc\","
                + "\"startTime\":\"" + LocalDateTime.now().plusMinutes(5) + "\","
                + "\"endTime\":\"" + LocalDateTime.now().plusHours(2) + "\""
                + "}";
        Map<?, ?> response = asMap(handler.handle(request, null));
        assertEquals("success", response.get("status"));

        assertEquals(1, clientHandler.sendCount);
        Map<?, ?> push = asMap(clientHandler.lastMessage);
        assertEquals("AUCTION_CREATED", push.get("push"));
        assertEquals("909", push.get("auctionId"));
        assertEquals("OPEN", push.get("auctionStatus"));
    }

    @Test
    public void handleEndAuctionAndRefreshStatusWithInjectedAuctionService() throws Exception {
        RequestHandler handler = new RequestHandler();
        FakeItemService itemService = new FakeItemService();
        FakeAuctionService auctionService = new FakeAuctionService(itemService);
        inject(handler, "auctionService", auctionService);

        String endReq = "{\"action\":\"end_auction\",\"sellerId\":\"s-10\",\"auctionId\":10}";
        Map<?, ?> endResp = asMap(handler.handle(endReq, null));
        assertEquals("success", endResp.get("status"));
        assertEquals(10.0, endResp.get("auctionId"));
        assertEquals("FINISHED", endResp.get("auctionStatus"));
        assertEquals("s-10", auctionService.lastEndSellerId);
        assertEquals(10, auctionService.lastEndAuctionId);

        Map<?, ?> refreshResp = asMap(handler.handle("{\"action\":\"refresh_auctions_status\"}", null));
        assertEquals("success", refreshResp.get("status"));
        assertTrue(auctionService.refreshCalled);
    }

    @Test
    public void handleUpdateWalletDepositCoversBidderAndSellerBranchesWithoutDaoHit() throws Exception {
        RequestHandler handler = new RequestHandler();

        Bidder bidder = new Bidder(201, "bid", "b@e", "pw");
        Seller seller = new Seller(202, "sell", "s@e", "pw");
        FakeUserService userService = new FakeUserService(List.of(bidder, seller));
        inject(handler, "userService", userService);

        Map<?, ?> bidderResp = asMap(handler.handle(
                "{\"action\":\"update_wallet_deposit\",\"userId\":201,\"amount\":-5}",
                null));
        assertEquals("error", bidderResp.get("status"));
        assertTrue(String.valueOf(bidderResp.get("message")).contains("khong thanh cong"));

        Map<?, ?> sellerResp = asMap(handler.handle(
                "{\"action\":\"update_wallet_deposit\",\"userId\":202,\"amount\":0}",
                null));
        assertEquals("error", sellerResp.get("status"));
        assertTrue(String.valueOf(sellerResp.get("message")).contains("khong thanh cong"));
    }

    @Test
    public void handleUserAndBidActionsCoverAdditionalErrorBranches() throws Exception {
        RequestHandler handler = new RequestHandler();

        Seller seller = new Seller(301, "sell", "s@e", "pw");
        seller.getWallet().deposit(150);
        FakeUserService userService = new FakeUserService(List.of(seller));
        FakeBidService bidService = new FakeBidService();
        FakeAuthService authService = new FakeAuthService(seller);
        inject(handler, "userService", userService);
        inject(handler, "bidService", bidService);
        inject(handler, "authService", authService);

        Map<?, ?> sellerById = asMap(handler.handle(
                "{\"action\":\"get_user_by_id\",\"userId\":301}",
                null));
        assertEquals("success", sellerById.get("status"));
        assertEquals(150.0, ((Number) sellerById.get("walletBalance")).doubleValue(), 0.0001);

        Map<?, ?> missingBidder = asMap(handler.handle(
                "{\"action\":\"place_bid\",\"auctionId\":\"10\",\"token\":\"token-fixed\",\"amount\":99}",
                null));
        assertEquals("error", missingBidder.get("status"));
        assertTrue(String.valueOf(missingBidder.get("message")).contains("bidder"));

        Map<?, ?> badEnd = asMap(handler.handle(
                "{\"action\":\"end_auction\",\"sellerId\":\"s\",\"auctionId\":\"bad\"}",
                null));
        assertEquals("error", badEnd.get("status"));
    }

    private Object invoke(Object target, String methodName, Class<?>[] parameterTypes, Object... args) {
        try {
            Method method = target.getClass().getDeclaredMethod(methodName, parameterTypes);
            method.setAccessible(true);
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new RuntimeException(cause);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private void inject(RequestHandler handler, String fieldName, Object value) throws Exception {
        Field field = RequestHandler.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(handler, value);
    }

    private Map<?, ?> asMap(String json) {
        return JsonUtils.fromJson(json, Map.class);
    }

    private static final class FakeAuthService extends AuthService {
        private final User loginUser;
        private String lastLogoutToken;

        private FakeAuthService(User loginUser) {
            this.loginUser = loginUser;
        }

        @Override
        public Map<String, Object> login(String username, String password) {
            return Map.of(
                    "accessToken", "token-fixed",
                    "refreshToken", "refresh-fixed",
                    "user", loginUser);
        }

        @Override
        public User getCurrentUser(String token) {
            return loginUser;
        }

        @Override
        public User authenticate(String token) {
            return loginUser;
        }

        @Override
        public void logout(String token) {
            lastLogoutToken = token;
        }
    }

    private static final class FakeUserService extends UserService {
        private final Map<Integer, User> usersById = new HashMap<>();

        private FakeUserService(List<User> users) {
            for (User user : users) {
                usersById.put(user.getId(), user);
            }
        }

        @Override
        public User register(String username, String email, String password, String role) {
            return new Bidder(999, username, email, password);
        }

        @Override
        public List<User> getAllUsers() {
            return new ArrayList<>(usersById.values());
        }

        @Override
        public User banUser(int id) {
            User user = usersById.get(id);
            if (user != null) {
                user.setStatus(UserStatus.BANNED);
            }
            return user;
        }

        @Override
        public User unbanUser(int id) {
            User user = usersById.get(id);
            if (user != null) {
                user.setStatus(UserStatus.LOGOUT);
            }
            return user;
        }

        @Override
        public Optional<User> findById(int id) {
            return Optional.ofNullable(usersById.get(id));
        }

        @Override
        public User switchRole(User user, String targetRole) {
            if ("BIDDER".equalsIgnoreCase(targetRole)) {
                User switched = new Bidder(user.getId(), user.getUsername(), user.getEmail(), user.getPassword());
                switched.setStatus(UserStatus.LOGIN);
                usersById.put(switched.getId(), switched);
                return switched;
            }
            return user;
        }
    }

    private static final class FakeBidService extends BidService {
        @Override
        public BidTransaction placeBid(String auctionId, Bidder bidder, double amount) {
            return new BidTransaction(700, Integer.parseInt(auctionId), bidder.getId(), amount);
        }

        @Override
        public List<BidTransaction> getBidderBidHistory(String bidderId) {
            return List.of(new BidTransaction(1, 10, Integer.parseInt(bidderId), 123.0));
        }

        @Override
        public List<BidTransaction> getAuctionBidHistory(String auctionId) {
            return List.of(new BidTransaction(2, Integer.parseInt(auctionId), 101, 234.0));
        }

        @Override
        public int registerAutoBid(int bidderId, int auctionId, double maxBid, double increment) {
            return 777;
        }
    }

    private static final class FakeItemService extends ItemService {
        private final List<Item> savedItems = new ArrayList<>();
        private int findByIdCallCount;

        @Override
        public Item createItem(Item item) {
            savedItems.add(item);
            return item;
        }

        @Override
        public Optional<Item> findById(int id) {
            // Force generateItemId() to loop at least once
            if (findByIdCallCount++ == 0) {
                return Optional.of(new Art(id, "x", "x", 1, "s", "a"));
            }
            return Optional.empty();
        }
    }

    private static final class FakeAuctionService extends AuctionService {
        private final FakeItemService itemService;
        private boolean refreshCalled;
        private String lastEndSellerId;
        private int lastEndAuctionId;

        private FakeAuctionService(FakeItemService itemService) {
            super(itemService);
            this.itemService = itemService;
        }

        @Override
        public Auction createAuction(String sellerId, int itemId, LocalDateTime startTime, LocalDateTime endTime) {
            Item item = itemService.savedItems.isEmpty() ? null : itemService.savedItems.getLast();
            return new Auction(909, item, sellerId, startTime, endTime);
        }

        @Override
        public Auction endAuctionBySeller(String sellerId, int auctionId) {
            lastEndSellerId = sellerId;
            lastEndAuctionId = auctionId;
            Auction auction = new Auction(auctionId, null, sellerId, LocalDateTime.now().minusHours(1), LocalDateTime.now());
            auction.setStatus(common.models.auction.AuctionStatus.FINISHED);
            auction.setCurrentHighestBid(1234);
            return auction;
        }

        @Override
        public void refreshAuctionsStatus() {
            refreshCalled = true;
        }
    }

    private static final class CapturingClientHandler extends ClientHandler {
        private final String id;
        private int sendCount;
        private String lastMessage;

        private CapturingClientHandler(String id) {
            super(new Socket(), new RequestHandler(), ConnectionManager.getInstance());
            this.id = id;
        }

        @Override
        public String getClientId() {
            return id;
        }

        @Override
        public void send(String message) {
            sendCount++;
            lastMessage = message;
        }

        @Override
        public void close() {
        }
    }
}
