package client.controller;

import client.application.ClientSession;
import client.network.TestSocketClient;
import common.models.auction.Auction;
import common.models.auction.AuctionStatus;
import common.models.item.Item;
import common.models.user.Bidder;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuctionDetailControllerUiLightTest extends JavaFxTestSupport {

    @BeforeEach
    void setUp() throws Exception {
        injectSharedSocket(new TestSocketClient());
    }

    @AfterEach
    void tearDown() {
        ClientSession.clear();
    }

    @Test
    void updateBidPanelStateCoversViewOnlyOwnClosedAndEnabledBranches() throws Exception {
        runOnFxThread(() -> {
            AuctionDetailController controller = new AuctionDetailController();
            VBox bidPanel = new VBox();
            TextField bidAmountField = new TextField();
            Button placeBidBtn = new Button();
            Label errorLabel = new Label();

            setField(controller, "bidPanel", bidPanel);
            setField(controller, "bidAmountField", bidAmountField);
            setField(controller, "placeBidBtn", placeBidBtn);
            setField(controller, "errorLabel", errorLabel);

            controller.setViewOnly(true);
            invoke(controller, "updateBidPanelState", new Class<?>[0]);
            assertFalse(bidPanel.isVisible());
            assertFalse(bidPanel.isManaged());

            controller.setViewOnly(false);
            Auction ownAuction = auction(1, "Phone", "7", AuctionStatus.RUNNING);
            setField(controller, "auction", ownAuction);
            setField(controller, "currentBidder", new Bidder(7, "bidder", "b@e", "p"));
            invoke(controller, "updateBidPanelState", new Class<?>[0]);
            return null;
        });
        flushFxEvents();

        runOnFxThread(() -> {
            AuctionDetailController controller = new AuctionDetailController();
            VBox bidPanel = new VBox();
            TextField bidAmountField = new TextField();
            Button placeBidBtn = new Button();
            Label errorLabel = new Label();

            setField(controller, "bidPanel", bidPanel);
            setField(controller, "bidAmountField", bidAmountField);
            setField(controller, "placeBidBtn", placeBidBtn);
            setField(controller, "errorLabel", errorLabel);
            setField(controller, "currentBidder", new Bidder(99, "bidder", "b@e", "p"));

            Auction closedAuction = auction(2, "Laptop", "7", AuctionStatus.FINISHED);
            setField(controller, "auction", closedAuction);
            invoke(controller, "updateBidPanelState", new Class<?>[0]);
            return null;
        });
        flushFxEvents();

        runOnFxThread(() -> {
            AuctionDetailController controller = new AuctionDetailController();
            VBox bidPanel = new VBox();
            TextField bidAmountField = new TextField();
            Button placeBidBtn = new Button();
            Label errorLabel = new Label();

            setField(controller, "bidPanel", bidPanel);
            setField(controller, "bidAmountField", bidAmountField);
            setField(controller, "placeBidBtn", placeBidBtn);
            setField(controller, "errorLabel", errorLabel);
            setField(controller, "currentBidder", new Bidder(99, "bidder", "b@e", "p"));

            Auction openAuction = auction(3, "Tablet", "7", AuctionStatus.RUNNING);
            setField(controller, "auction", openAuction);
            invoke(controller, "updateBidPanelState", new Class<?>[0]);
            assertTrue(bidPanel.isVisible());
            assertTrue(bidPanel.isManaged());
            assertFalse(bidAmountField.isDisabled());
            assertFalse(placeBidBtn.isDisabled());
            return null;
        });
    }

    @Test
    void updateBidPanelStateShowsExpectedErrorMessages() throws Exception {
        Object[] refs = new Object[4];

        runOnFxThread(() -> {
            AuctionDetailController controller = new AuctionDetailController();
            TextField bidAmountField = new TextField();
            Button placeBidBtn = new Button();
            Label errorLabel = new Label();
            refs[0] = controller;
            refs[1] = bidAmountField;
            refs[2] = placeBidBtn;
            refs[3] = errorLabel;

            VBox bidPanel = new VBox();
            setField(controller, "bidPanel", bidPanel);
            setField(controller, "bidAmountField", bidAmountField);
            setField(controller, "placeBidBtn", placeBidBtn);
            setField(controller, "errorLabel", errorLabel);
            setField(controller, "auction", auction(4, "Camera", "7", AuctionStatus.RUNNING));
            setField(controller, "currentBidder", new Bidder(7, "bidder", "b@e", "p"));
            invoke(controller, "updateBidPanelState", new Class<?>[0]);
            return null;
        });
        flushFxEvents();

        runOnFxThread(() -> {
            Label errorLabel = (Label) refs[3];
            TextField bidAmountField = (TextField) refs[1];
            Button placeBidBtn = (Button) refs[2];
            assertEquals("Bạn không thể tự đấu giá sản phẩm của chính mình.", errorLabel.getText());
            assertTrue(errorLabel.isVisible());
            assertTrue(bidAmountField.isDisabled());
            assertTrue(placeBidBtn.isDisabled());
            return null;
        });

        runOnFxThread(() -> {
            AuctionDetailController controller = (AuctionDetailController) refs[0];
            Label errorLabel = (Label) refs[3];
            errorLabel.setVisible(false);
            setField(controller, "auction", auction(5, "Camera", "8", AuctionStatus.CANCELED));
            setField(controller, "currentBidder", new Bidder(7, "bidder", "b@e", "p"));
            invoke(controller, "updateBidPanelState", new Class<?>[0]);
            return null;
        });
        flushFxEvents();

        runOnFxThread(() -> {
            Label errorLabel = (Label) refs[3];
            assertEquals("Phiên đấu giá đã đóng.", errorLabel.getText());
            assertTrue(errorLabel.isVisible());
            return null;
        });
    }

    private Auction auction(int id, String name, String sellerId, AuctionStatus status) {
        LocalDateTime now = LocalDateTime.now();
        Auction auction = new Auction(id, item(name, sellerId), sellerId, now.minusMinutes(1), now.plusMinutes(10));
        auction.setStatus(status);
        return auction;
    }

    private Item item(String name, String sellerId) {
        return new Item(1, name, "desc", 10, sellerId) {
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
}
