package client.controller;

import common.models.auction.Auction;
import common.models.auction.BidTransaction;
import common.models.user.Bidder;
import common.utils.FormatUtils;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.WindowEvent;
import javafx.util.Duration;
import server.manager.AutoBidManager;
import server.repository.AuctionDAO;
import server.service.AuctionService;
import server.service.BidService;
import server.service.ItemService;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AuctionDetailController {
    // Static list tracking all open AuctionDetail windows
    private static final List<javafx.stage.Stage> OPEN_STAGES = new ArrayList<>();

    /**
     * Register a stage to be tracked. Called when opening a new AuctionDetail
     * window.
     */
    public static void registerStage(javafx.stage.Stage stage) {
        if (stage != null) {
            OPEN_STAGES.add(stage);
        }
    }

    /**
     * Close all open AuctionDetail windows.
     */
    public static void closeAllWindows() {
        List<javafx.stage.Stage> stagesCopy = new ArrayList<>(OPEN_STAGES);
        for (javafx.stage.Stage stage : stagesCopy) {
            try {
                stage.close();
            } catch (Exception e) {
                // Ignore if already closed
            }
        }
        OPEN_STAGES.clear();
    }

    @FXML
    private Label itemNameLabel;
    @FXML
    private Label statusLabel;
    @FXML
    private Label countdownLabel;
    @FXML
    private Label currentBidLabel;
    @FXML
    private Label lblProductName;
    @FXML
    private Label lblProductType;
    @FXML
    private Label lblSellerId;
    @FXML
    private Label lblStartPrice;
    @FXML
    private TextArea txtDescription;
    @FXML
    private TextField bidAmountField;
    @FXML
    private Label errorLabel;
    @FXML
    private VBox bidPanel;
    @FXML
    private LineChart<Number, Number> bidChart;
    @FXML
    private TableView<BidTransaction> historyTable;
    @FXML
    private TableColumn<BidTransaction, String> bidderCol;
    @FXML
    private TableColumn<BidTransaction, String> amountCol;
    @FXML
    private TableColumn<BidTransaction, String> timeCol;
    @FXML
    private Button placeBidBtn;

    // Auto-Bid fields
    @FXML
    private TextField maxBidField;
    @FXML
    private TextField incrementField;
    @FXML
    private ToggleButton autoBidToggle;
    @FXML
    private Label autoBidStatusLabel;
    @FXML
    private VBox autoBidSection;

    private final BidService bidService = new BidService();
    private final AuctionService auctionService = new AuctionService(new ItemService());
    private final AuctionDAO auctionDAO = new AuctionDAO();
    private final AutoBidManager autoBidManager = AutoBidManager.getInstance();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ObservableList<BidTransaction> historyRows = FXCollections.observableArrayList();
    private Timeline refreshTimeline;
    private Auction auction;
    private Bidder currentBidder;
    private boolean viewOnly = false;
    private int currentAgentId = -1; // -1 = chưa có agent

    @FXML
    private void initialize() {
        bidderCol.setCellValueFactory(v -> new SimpleStringProperty(String.valueOf(v.getValue().getBidderId())));
        amountCol.setCellValueFactory(
                v -> new SimpleStringProperty(FormatUtils.formatCurrency(v.getValue().getBidAmount())));
        timeCol.setCellValueFactory(v -> new SimpleStringProperty(
                FormatUtils.formatDateTimeWithSeconds(v.getValue().getBidTime())));
        historyTable.setItems(historyRows);
        historyTable.setPlaceholder(new Label("Chưa có lượt đặt giá nào"));
        Platform.runLater(() -> {
            if (itemNameLabel.getScene() != null && itemNameLabel.getScene().getWindow() != null) {
                itemNameLabel.getScene().getWindow().addEventHandler(WindowEvent.WINDOW_HIDDEN, e -> shutdown());
            }
        });
    }

    public void setAuction(Auction auction, Bidder bidder) {
        this.auction = auction;
        this.currentBidder = bidder;
        updateBidPanelState();
        updateHeader();
        refreshDataAsync();
        startAutoRefresh();
        checkExistingAutoBid();
    }

    private void setBidPanelVisible(boolean visible) {
        bidPanel.setVisible(visible);
        bidPanel.setManaged(visible);
    }

    @FXML
    private void handlePlaceBid() {
        if (auction == null || currentBidder == null) {
            showError("Không thể đặt giá ở thời điểm này.");
            return;
        }

        if (isOwnAuction()) {
            showError("Bạn không thể tự đấu giá sản phẩm của chính mình.");
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(bidAmountField.getText().trim());
        } catch (NumberFormatException e) {
            showError("Số tiền đặt phải là số.");
            return;
        }

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                bidService.placeBid(String.valueOf(auction.getAuctionId()), currentBidder, amount);
                return null;
            }
        };
        task.setOnSucceeded(event -> {
            bidAmountField.clear();
            hideError();
            refreshDataAsync();
        });
        task.setOnFailed(event -> showError(
                task.getException() == null ? "Đặt giá thất bại." : task.getException().getMessage()));
        executor.submit(task);
    }

    // ====== AUTO-BID HANDLER ======

    @FXML
    private void handleToggleAutoBid() {
        if (auction == null || currentBidder == null) {
            autoBidToggle.setSelected(false);
            showError("Không thể bật auto-bid lúc này.");
            return;
        }

        if (autoBidToggle.isSelected()) {
            enableAutoBid();
        } else {
            disableAutoBid();
        }
    }

    private void enableAutoBid() {
        String maxBidText = maxBidField.getText().trim();
        String incrementText = incrementField.getText().trim();

        if (maxBidText.isEmpty() || incrementText.isEmpty()) {
            showError("Vui lòng nhập giá trần và bước giá.");
            autoBidToggle.setSelected(false);
            return;
        }

        double maxBid, increment;
        try {
            maxBid = Double.parseDouble(maxBidText);
            increment = Double.parseDouble(incrementText);
        } catch (NumberFormatException e) {
            showError("Giá trần và bước giá phải là số.");
            autoBidToggle.setSelected(false);
            return;
        }

        if (maxBid <= 0 || increment <= 0) {
            showError("Giá trần và bước giá phải lớn hơn 0.");
            autoBidToggle.setSelected(false);
            return;
        }

        if (maxBid <= auction.getCurrentHighestBid()) {
            showError("Giá trần phải lớn hơn giá hiện tại ("
                    + FormatUtils.formatCurrency(auction.getCurrentHighestBid()) + ").");
            autoBidToggle.setSelected(false);
            return;
        }

        final double fMaxBid = maxBid;
        final double fIncrement = increment;

        Task<Integer> task = new Task<>() {
            @Override
            protected Integer call() {
                return autoBidManager.registerAgent(
                        currentBidder.getId(),
                        auction.getAuctionId(),
                        fMaxBid,
                        fIncrement);
            }
        };

        task.setOnSucceeded(event -> {
            int agentId = task.getValue();
            if (agentId > 0) {
                currentAgentId = agentId;
                autoBidToggle.setText("TẮT AUTO");
                autoBidStatusLabel.setText("✓ Đang chạy (max: " + FormatUtils.formatCurrency(fMaxBid) + ")");
                autoBidStatusLabel.setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");
                maxBidField.setDisable(true);
                incrementField.setDisable(true);
                hideError();
                System.out.println("Auto-Bid: Đã bật cho auction " + auction.getAuctionId() + ", agent ID: " + agentId);
            } else {
                autoBidToggle.setSelected(false);
                showError("Không thể đăng ký auto-bid.");
            }
        });

        task.setOnFailed(event -> {
            autoBidToggle.setSelected(false);
            showError("Lỗi khi đăng ký auto-bid: "
                    + (task.getException() == null ? "" : task.getException().getMessage()));
        });

        executor.submit(task);
    }

    private void disableAutoBid() {
        if (currentAgentId < 0) {
            autoBidStatusLabel.setText("Chưa kích hoạt");
            autoBidStatusLabel.setStyle("-fx-text-fill: #7f8c8d;");
            return;
        }

        final int agentIdToCancel = currentAgentId;

        Task<Boolean> task = new Task<>() {
            @Override
            protected Boolean call() {
                return autoBidManager.cancelAgent(agentIdToCancel);
            }
        };

        task.setOnSucceeded(event -> {
            boolean cancelled = task.getValue();
            if (cancelled) {
                currentAgentId = -1;
                autoBidToggle.setText("BẬT AUTO");
                autoBidStatusLabel.setText("✗ Đã tắt");
                autoBidStatusLabel.setStyle("-fx-text-fill: #e74c3c;");
                maxBidField.setDisable(false);
                incrementField.setDisable(false);
                hideError();
                System.out.println("Auto-Bid: Đã tắt cho auction " + auction.getAuctionId());
            } else {
                showError("Không thể hủy auto-bid.");
            }
        });

        task.setOnFailed(event -> {
            showError("Lỗi khi hủy auto-bid: "
                    + (task.getException() == null ? "" : task.getException().getMessage()));
        });

        executor.submit(task);
    }

    private void checkExistingAutoBid() {
        if (currentBidder == null || auction == null) {
            return;
        }

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                var agent = autoBidManager.getAgent(currentBidder.getId(), auction.getAuctionId());
                if (agent != null) {
                    currentAgentId = agent.getAgentId();
                }
                return null;
            }
        };

        task.setOnSucceeded(event -> {
            if (currentAgentId > 0) {
                var agent = autoBidManager.getAgent(currentBidder.getId(), auction.getAuctionId());
                if (agent != null) {
                    autoBidToggle.setSelected(true);
                    autoBidToggle.setText("TẮT AUTO");
                    autoBidStatusLabel
                            .setText("✓ Đang chạy (max: " + FormatUtils.formatCurrency(agent.getMaxBid()) + ")");
                    autoBidStatusLabel.setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");
                    maxBidField.setText(String.valueOf((long) agent.getMaxBid()));
                    incrementField.setText(String.valueOf((long) agent.getIncrement()));
                    maxBidField.setDisable(true);
                    incrementField.setDisable(true);
                }
            }
        });

        executor.submit(task);
    }

    // ====== END AUTO-BID ======

    private void startAutoRefresh() {
        if (refreshTimeline != null) {
            refreshTimeline.stop();
        }
        refreshTimeline = new Timeline(new KeyFrame(Duration.seconds(2), event -> refreshDataAsync()));
        refreshTimeline.setCycleCount(Timeline.INDEFINITE);
        refreshTimeline.play();
    }

    private void refreshDataAsync() {
        if (auction == null) {
            return;
        }

        Task<ObservableList<BidTransaction>> task = new Task<>() {
            @Override
            protected ObservableList<BidTransaction> call() {
                auctionService.refreshAuctionsStatus();
                auction = auctionDAO.findById(auction.getAuctionId()).orElse(auction);

                return FXCollections.observableArrayList(
                        bidService.getAuctionBidHistory(String.valueOf(auction.getAuctionId())));
            }
        };

        task.setOnSucceeded(event -> {
            ObservableList<BidTransaction> rows = task.getValue();
            historyRows.setAll(rows);
            renderChart(rows);
            updateHeader();
            updateBidPanelState();
        });
        task.setOnFailed(event -> showError(
                task.getException() == null ? "Không thể cập nhật dữ liệu." : task.getException().getMessage()));
        executor.submit(task);
    }

    private void renderChart(ObservableList<BidTransaction> rows) {
        XYChart.Series<Number, Number> series = new XYChart.Series<>();
        series.setName("Giá đặt");
        int index = 1;
        for (BidTransaction row : rows) {
            series.getData().add(new XYChart.Data<>(index++, row.getBidAmount()));
        }
        bidChart.getData().setAll(series);
    }

    private void updateHeader() {
        if (auction == null) {
            return;
        }
        String itemName = auction.getItem() == null ? "Auction" : auction.getItem().getName();
        itemNameLabel.setText(itemName);
        statusLabel.setText(String.valueOf(auction.getStatus()));
        applyStatusStyle();
        currentBidLabel.setText(FormatUtils.formatCurrency(auction.getCurrentHighestBid()));
        countdownLabel.setText("Thời gian còn lại: " + remainingTimeText());
        if (auction.getItem() != null) {
            lblProductName.setText(itemName);
            lblProductType.setText(auction.getItem().getClass().getSimpleName());
            lblSellerId.setText(auction.getSellerId());
            lblStartPrice.setText(FormatUtils.formatCurrency(auction.getItem().getStartingPrice()));
            txtDescription
                    .setText(auction.getItem().getDescription() == null ? "" : auction.getItem().getDescription());
        } else {
            lblProductName.setText("-");
            lblProductType.setText("-");
            lblSellerId.setText("-");
            lblStartPrice.setText("-");
            txtDescription.setText("");
        }
    }

    private void updateBidPanelState() {
        if (viewOnly) {
            setBidPanelVisible(false);
            hideError();
            return;
        }
        boolean canBid = currentBidder != null && !isOwnAuction();
        setBidPanelVisible(canBid);
        if (currentBidder != null && isOwnAuction()) {
            showError("Bạn không thể tự đấu giá sản phẩm của chính mình.");
            return;
        }
        if (isAuctionClosed()) {
            setBidInputEnabled(false);
            showError("Phiên đấu giá đã đóng.");
            return;
        }
        setBidInputEnabled(true);
        hideError();
    }

    private void applyStatusStyle() {
        statusLabel.getStyleClass().removeIf(style -> style.startsWith("status-"));
        statusLabel.getStyleClass().add("status-pill");
        if (auction.getStatus() == null) {
            statusLabel.getStyleClass().add("status-open");
            return;
        }

        switch (auction.getStatus()) {
            case RUNNING -> statusLabel.getStyleClass().add("status-running");
            case OPEN -> statusLabel.getStyleClass().add("status-open");
            case FINISHED -> statusLabel.getStyleClass().add("status-finished");
            case PAID -> statusLabel.getStyleClass().add("status-paid");
            case CANCELED -> statusLabel.getStyleClass().add("status-canceled");
        }
    }

    private String remainingTimeText() {
        if (auction == null || auction.getEndTime() == null) {
            return "--:--:--";
        }
        LocalDateTime now = LocalDateTime.now();
        if (!now.isBefore(auction.getEndTime())) {
            return "00:00:00";
        }
        java.time.Duration d = java.time.Duration.between(now, auction.getEndTime());
        long totalSeconds = d.getSeconds();
        long h = totalSeconds / 3600;
        long m = (totalSeconds % 3600) / 60;
        long s = totalSeconds % 60;
        return String.format("%02d:%02d:%02d", h, m, s);
    }

    private void showError(String message) {
        Platform.runLater(() -> {
            errorLabel.setText(message);
            errorLabel.setVisible(true);
        });
    }

    private void hideError() {
        errorLabel.setVisible(false);
        errorLabel.setText("");
    }

    private boolean isOwnAuction() {
        if (auction == null || currentBidder == null || auction.getSellerId() == null) {
            return false;
        }
        return auction.getSellerId().trim().equals(String.valueOf(currentBidder.getId()));
    }

    private void shutdown() {
        if (refreshTimeline != null) {
            refreshTimeline.stop();
        }
        executor.shutdownNow();
    }

    public void setViewOnly(boolean viewOnly) {
        this.viewOnly = viewOnly;
    }

    private boolean isAuctionClosed() {
        if (auction == null || auction.getStatus() == null)
            return false;
        return auction.getStatus() == common.models.auction.AuctionStatus.FINISHED
                || auction.getStatus() == common.models.auction.AuctionStatus.PAID
                || auction.getStatus() == common.models.auction.AuctionStatus.CANCELED;
    }

    private void setBidInputEnabled(boolean enabled) {
        if (bidAmountField != null)
            bidAmountField.setDisable(!enabled);
        if (placeBidBtn != null)
            placeBidBtn.setDisable(!enabled);
    }

}