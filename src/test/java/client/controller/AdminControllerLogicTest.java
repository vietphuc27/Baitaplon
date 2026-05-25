package client.controller;

import client.application.ClientSession;
import client.network.TestSocketClient;
import javafx.concurrent.Task;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Comparator;
import java.util.concurrent.ExecutorService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminControllerLogicTest {

    @BeforeEach
    void setUp() throws Exception {
        injectSharedSocket(new TestSocketClient());
    }

    @AfterEach
    void tearDown() {
        ClientSession.clear();
    }

    @Test
    void parseAmountHandlesFormattedAndInvalidValues() throws Exception {
        AdminController controller = new AdminController();

        double parsed = (double) invoke(controller, "parseAmount", new Class<?>[] { String.class }, "$1,234.50");
        double invalid = (double) invoke(controller, "parseAmount", new Class<?>[] { String.class }, "abc");

        assertEquals(1234.50, parsed, 0.0001);
        assertEquals(0.0, invalid, 0.0001);
    }

    @Test
    void comparatorsUseRequestedFields() throws Exception {
        AdminController controller = new AdminController();

        Comparator<AdminController.UserRow> userComparator = (Comparator<AdminController.UserRow>) invoke(controller,
                "resolveUserComparator", new Class<?>[] { String.class }, "Role");
        AdminController.UserRow bidder = new AdminController.UserRow("1", "u1", "u1@e", "BIDDER", "LOGIN");
        AdminController.UserRow seller = new AdminController.UserRow("2", "u2", "u2@e", "SELLER", "LOGIN");
        assertTrue(userComparator.compare(bidder, seller) < 0);

        Comparator<AdminController.AuctionRow> auctionComparator = (Comparator<AdminController.AuctionRow>) invoke(
                controller, "resolveAuctionComparator", new Class<?>[] { String.class }, "Current bid");
        AdminController.AuctionRow low = new AdminController.AuctionRow("1", "A", "$100", "OPEN");
        AdminController.AuctionRow high = new AdminController.AuctionRow("2", "B", "$1,000", "OPEN");
        assertTrue(auctionComparator.compare(low, high) < 0);
    }

    @Test
    void comparatorsCoverDefaultAndOtherSortModes() throws Exception {
        AdminController controller = new AdminController();

        Comparator<AdminController.UserRow> defaultUserComparator = (Comparator<AdminController.UserRow>) invoke(
                controller, "resolveUserComparator", new Class<?>[] { String.class }, "Username");
        Comparator<AdminController.UserRow> statusUserComparator = (Comparator<AdminController.UserRow>) invoke(
                controller, "resolveUserComparator", new Class<?>[] { String.class }, "Status");
        AdminController.UserRow a = new AdminController.UserRow("1", "alice", "a@e", "BIDDER", "LOGIN");
        AdminController.UserRow b = new AdminController.UserRow("2", "bob", "b@e", "SELLER", "BANNED");
        assertTrue(defaultUserComparator.compare(a, b) < 0);
        assertTrue(statusUserComparator.compare(a, b) > 0);

        Comparator<AdminController.AuctionRow> byItem = (Comparator<AdminController.AuctionRow>) invoke(controller,
                "resolveAuctionComparator", new Class<?>[] { String.class }, "Item name");
        Comparator<AdminController.AuctionRow> byStatus = (Comparator<AdminController.AuctionRow>) invoke(controller,
                "resolveAuctionComparator", new Class<?>[] { String.class }, "Status");
        Comparator<AdminController.AuctionRow> byDefaultId = (Comparator<AdminController.AuctionRow>) invoke(
                controller, "resolveAuctionComparator", new Class<?>[] { String.class }, "ID");

        AdminController.AuctionRow r1 = new AdminController.AuctionRow("10", "Phone", "$abc", "RUNNING");
        AdminController.AuctionRow r2 = new AdminController.AuctionRow("2", "Laptop", "$xyz", "OPEN");
        assertTrue(byItem.compare(r1, r2) > 0);
        assertTrue(byStatus.compare(r1, r2) > 0);
        assertTrue(byDefaultId.compare(r1, r2) > 0);
    }

    @Test
    void taskErrorMessageUsesFallbackAndExceptionMessage() throws Exception {
        AdminController controller = new AdminController();
        Task<Object> emptyTask = new Task<>() {
            @Override
            protected Object call() {
                return null;
            }
        };
        assertEquals("Không tải được dữ liệu.",
                invoke(controller, "getTaskErrorMessage", new Class<?>[] { Task.class }, emptyTask));
    }

    @Test
    void shutdownStopsBackgroundExecutor() throws Exception {
        AdminController controller = new AdminController();
        ExecutorService executor = (ExecutorService) field(controller, "backgroundExecutor");
        assertTrue(!executor.isShutdown());
        invoke(controller, "shutdown", new Class<?>[0]);
        assertTrue(executor.isShutdown());
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

    private Object field(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

}
