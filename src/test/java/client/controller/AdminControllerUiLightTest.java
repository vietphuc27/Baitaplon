package client.controller;

import client.application.ClientSession;
import client.network.TestSocketClient;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminControllerUiLightTest extends JavaFxTestSupport {
    private TestSocketClient socketClient;

    @BeforeEach
    void setUp() throws Exception {
        socketClient = new TestSocketClient();
        injectSharedSocket(socketClient);
        seedAdminResponses();
    }

    @AfterEach
    void tearDown() {
        ClientSession.clear();
    }

    @Test
    void initializeAndSearchLoadFilteredRowsWithoutStage() throws Exception {
        AdminController controller = runOnFxThread(() -> {
            AdminController c = new AdminController();
            injectControls(c);
            c.initialize();
            return c;
        });

        TableView<?> userTable = table(controller, "userTable");
        TableView<?> auctionTable = table(controller, "auctionTable");
        waitUntil(() -> userTable.getItems().size() == 3 && auctionTable.getItems().size() == 3);

        runOnFxThread(() -> {
            assertEquals("ALL", combo(controller, "cbFilterStatus").getValue());
            assertEquals("Username", combo(controller, "cbSortBy").getValue());
            assertEquals("ID", combo(controller, "cbAuctionSortBy").getValue());

            text(controller, "txtSearch").setText("seller");
            combo(controller, "cbFilterStatus").setValue("LOGIN");
            combo(controller, "cbSortBy").setValue("Role");
            controller.searchUsers();

            text(controller, "txtAuctionSearch").setText("phone");
            combo(controller, "cbAuctionFilterStatus").setValue("RUNNING");
            combo(controller, "cbAuctionSortBy").setValue("Current bid");
            controller.searchAuctions();
            return null;
        });
        waitUntil(() -> userTable.getItems().size() == 1 && auctionTable.getItems().size() == 1);

        runOnFxThread(() -> {
            assertEquals("sellerUser", fieldValue(userTable.getItems().get(0), "username"));
            assertEquals("Phone", fieldValue(auctionTable.getItems().get(0), "itemName"));
            invoke(controller, "shutdown", new Class<?>[0]);
            return null;
        });
    }

    @Test
    void selectedAdminActionsCallSocketAndRefreshData() throws Exception {
        AdminController controller = runOnFxThread(() -> {
            AdminController c = new AdminController();
            injectControls(c);
            table(c, "userTable").getItems().add(new AdminController.UserRow(
                    "2", "sellerUser", "seller@e.com", "SELLER", "LOGIN"));
            table(c, "auctionTable").getItems().add(new AdminController.AuctionRow(
                    "10", "Phone", "$100", "RUNNING"));
            return c;
        });

        runOnFxThread(() -> {
            table(controller, "userTable").getSelectionModel().selectFirst();
            controller.banUser();
            controller.activateUser();

            table(controller, "auctionTable").getSelectionModel().selectFirst();
            controller.cancelAuction();
            return null;
        });

        waitUntil(() -> socketClient.getSyncActions().contains("ban_user")
                && socketClient.getSyncActions().contains("unban_user")
                && socketClient.getSyncActions().contains("cancel_auction"));

        assertTrue(socketClient.getSyncActions().contains("get_all_users"));
        assertTrue(socketClient.getSyncActions().contains("get_all_auctions"));
        runOnFxThread(() -> {
            invoke(controller, "shutdown", new Class<?>[0]);
            return null;
        });
    }

    private void seedAdminResponses() {
        socketClient.setResponse("get_all_users", Map.of(
                "status", "success",
                "users", List.of(
                        Map.of("userId", 1, "username", "adminUser", "email", "admin@e.com", "role", "ADMIN",
                                "userStatus", "LOGOUT"),
                        Map.of("userId", 2, "username", "sellerUser", "email", "seller@e.com", "role", "SELLER",
                                "userStatus", "LOGIN"),
                        Map.of("userId", 3, "username", "bidderUser", "email", "bidder@e.com", "role", "BIDDER",
                                "userStatus", "BANNED"))));
        socketClient.setResponse("get_all_auctions", Map.of(
                "status", "success",
                "auctions", List.of(
                        auctionMap(10, "Phone", "RUNNING", 250.0),
                        auctionMap(11, "Laptop", "OPEN", 100.0),
                        auctionMap(12, "Camera", "FINISHED", 500.0))));
        socketClient.setResponse("ban_user", Map.of("status", "success"));
        socketClient.setResponse("unban_user", Map.of("status", "success"));
        socketClient.setResponse("cancel_auction", Map.of("status", "success"));
    }

    private Map<String, Object> auctionMap(int id, String itemName, String status, double currentPrice) {
        return Map.of(
                "auctionId", id,
                "itemName", itemName,
                "sellerId", "2",
                "auctionStatus", status,
                "currentPrice", currentPrice,
                "startingPrice", currentPrice);
    }

    private void injectControls(AdminController controller) throws Exception {
        setField(controller, "txtSearch", new TextField());
        setField(controller, "cbFilterStatus", new ComboBox<String>());
        setField(controller, "cbSortBy", new ComboBox<String>());
        setField(controller, "txtAuctionSearch", new TextField());
        setField(controller, "cbAuctionFilterStatus", new ComboBox<String>());
        setField(controller, "cbAuctionSortBy", new ComboBox<String>());
        setField(controller, "userTable", new TableView<AdminController.UserRow>());
        setField(controller, "userIdCol", new TableColumn<AdminController.UserRow, String>());
        setField(controller, "usernameCol", new TableColumn<AdminController.UserRow, String>());
        setField(controller, "emailCol", new TableColumn<AdminController.UserRow, String>());
        setField(controller, "roleCol", new TableColumn<AdminController.UserRow, String>());
        setField(controller, "statusCol", new TableColumn<AdminController.UserRow, String>());
        setField(controller, "auctionTable", new TableView<AdminController.AuctionRow>());
        setField(controller, "auctionIdCol", new TableColumn<AdminController.AuctionRow, String>());
        setField(controller, "itemNameCol", new TableColumn<AdminController.AuctionRow, String>());
        setField(controller, "currentBidCol", new TableColumn<AdminController.AuctionRow, String>());
        setField(controller, "auctionStatusCol", new TableColumn<AdminController.AuctionRow, String>());
    }

    private void waitUntil(Condition condition) throws Exception {
        long timeoutAt = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < timeoutAt) {
            boolean done = runOnFxThread(() -> condition.check());
            if (done) {
                return;
            }
            Thread.sleep(25);
        }
        assertTrue(runOnFxThread(() -> condition.check()));
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

    private Object invoke(Object target, String methodName, Class<?>[] paramTypes, Object... args) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName, paramTypes);
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    @SuppressWarnings("unchecked")
    private TableView<Object> table(AdminController controller, String fieldName) throws Exception {
        return (TableView<Object>) field(controller, fieldName);
    }

    @SuppressWarnings("unchecked")
    private ComboBox<String> combo(AdminController controller, String fieldName) throws Exception {
        return (ComboBox<String>) field(controller, fieldName);
    }

    private TextField text(AdminController controller, String fieldName) throws Exception {
        return (TextField) field(controller, fieldName);
    }

    private Object field(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    private String fieldValue(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return String.valueOf(field.get(target));
    }

    @FunctionalInterface
    private interface Condition {
        boolean check() throws Exception;
    }
}
