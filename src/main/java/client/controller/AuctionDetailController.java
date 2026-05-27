package client.controller;

import client.application.ClientSession;
import client.network.BidClient;
import client.network.SocketClient;
import common.models.auction.Auction;
import common.models.auction.BidTransaction;
import common.models.user.Bidder;
import common.utils.FormatUtils;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AuctionDetailController {
    private static final long ERROR_MIN_DISPLAY_MILLIS = 2500L;

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

    private volatile boolean refreshInProgress = false;
    private volatile boolean refreshQueued = false;
    private String lastHistorySignature = "";
    private volatile long errorVisibleSinceMillis = 0L;

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
    private ImageView imgProduct;
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

    private final BidClient bidClient = new BidClient();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ObservableList<BidTransaction> historyRows = FXCollections.observableArrayList();
    private final SocketClient.PushListener auctionPushListener = this::handlePushEvent;
    private Auction auction;
    private Bidder currentBidder;
    private boolean viewOnly = false;
    private int currentAgentId = -1; // -1 = chưa có agent
    private Stage imagePreviewStage;
    private String currentProductImageUrl;

    @FXML
    private void initialize() {
        bidderCol.setCellValueFactory(v -> new SimpleStringProperty(String.valueOf(v.getValue().getBidderId())));
        amountCol.setCellValueFactory(
                v -> new SimpleStringProperty(FormatUtils.formatCurrency(v.getValue().getBidAmount())));
        timeCol.setCellValueFactory(v -> new SimpleStringProperty(
                FormatUtils.formatDateTimeWithSeconds(v.getValue().getBidTime())));
        historyTable.setItems(historyRows);
        historyTable.setPlaceholder(new Label("Chưa có lượt đặt giá nào"));
        imgProduct.setOnMouseClicked(event -> openImagePreview());
        updateImageInteractionState(false);
        Platform.runLater(() -> {
            if (itemNameLabel.getScene() != null && itemNameLabel.getScene().getWindow() != null) {
                itemNameLabel.getScene().getWindow().addEventHandler(WindowEvent.WINDOW_HIDDEN, e -> shutdown());
            }
        });
    }

    public void setAuction(Auction auction, Bidder bidder) {
        this.auction = auction;
        this.currentBidder = bidder;
        historyRows.clear();
        updateBidPanelState();
        updateHeader();
        refreshDataAsync();
        registerAuctionPushListener();
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
                bidClient.placeBid(String.valueOf(auction.getAuctionId()), amount);
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
                return bidClient.registerAutoBid(
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
        currentAgentId = -1;

        Task<Boolean> task = new Task<>() {
            @Override
            protected Boolean call() {
                return bidClient.cancelAutoBid(agentIdToCancel);
            }
        };

        task.setOnSucceeded(event -> {
            boolean cancelled = task.getValue();
            if (cancelled) {
                refreshDataAsync();
                autoBidToggle.setText("BẬT AUTO");
                autoBidStatusLabel.setText("✗ Đã tắt");
                autoBidStatusLabel.setStyle("-fx-text-fill: #e74c3c;");
                maxBidField.setDisable(false);
                incrementField.setDisable(false);
                hideError();
                System.out.println("Auto-Bid: Đã tắt cho auction " + auction.getAuctionId()
                        + ", cancelled immediately=" + cancelled);
            } else {
                showError("Không thể hủy auto-bid.");
            }
        });

        task.setOnFailed(event -> showError("Lỗi khi hủy auto-bid: "
                + (task.getException() == null ? "" : task.getException().getMessage())));

        executor.submit(task);
    }

    private void checkExistingAutoBid() {
        if (currentBidder == null || auction == null) {
            return;
        }

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                Map<String, Object> status = bidClient.getAutoBidStatus(currentBidder.getId(), auction.getAuctionId());
                boolean active = Boolean.parseBoolean(String.valueOf(status.getOrDefault("active", false)));
                if (active) {
                    Object agentIdRaw = status.get("agentId");
                    if (agentIdRaw instanceof Number n) {
                        currentAgentId = n.intValue();
                    } else {
                        currentAgentId = Integer.parseInt(String.valueOf(agentIdRaw));
                    }
                } else {
                    currentAgentId = -1;
                }
                return null;
            }
        };

        task.setOnSucceeded(event -> {
            if (currentAgentId > 0) {
                try {
                    Map<String, Object> status = bidClient.getAutoBidStatus(currentBidder.getId(),
                            auction.getAuctionId());
                    double maxBid = status.get("maxBid") instanceof Number n ? n.doubleValue() : 0.0;
                    double increment = status.get("increment") instanceof Number n ? n.doubleValue() : 0.0;

                    autoBidToggle.setSelected(true);
                    autoBidToggle.setText("TẮT AUTO");
                    autoBidStatusLabel
                            .setText("✓ Đang chạy (max: " + FormatUtils.formatCurrency(maxBid) + ")");
                    autoBidStatusLabel.setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");
                    maxBidField.setText(String.valueOf((long) maxBid));
                    incrementField.setText(String.valueOf((long) increment));
                    maxBidField.setDisable(true);
                    incrementField.setDisable(true);
                } catch (RuntimeException ignored) {
                }
            }
        });

        executor.submit(task);
    }

    // ====== END AUTO-BID ======

    private void registerAuctionPushListener() {
        try {
            ClientSession.getSocket().addPushListener(auctionPushListener);
        } catch (RuntimeException e) {
            showError("Khong the dang ky kenh cap nhat realtime.");
        }
    }

    private void handlePushEvent(String event, Map<String, Object> data) {
        if (auction == null || data == null || !data.containsKey("auctionId")) {
            return;
        }

        String eventAuctionId = String.valueOf(data.get("auctionId")).trim();
        if (!eventAuctionId.equals(String.valueOf(auction.getAuctionId()))) {
            return;
        }

        if (!"BID_PLACED".equals(event) && !"AUCTION_ENDED".equals(event) && !"AUCTION_CANCELED".equals(event)) {
            return;
        }

        Platform.runLater(this::refreshDataAsync);
    }

    private void refreshDataAsync() {
        if (auction == null) {
            return;
        }
        if (refreshInProgress) {
            refreshQueued = true;
            return;
        }
        refreshInProgress = true;

        Task<ObservableList<BidTransaction>> task = new Task<>() {
            @Override
            protected ObservableList<BidTransaction> call() {
                try {
                    bidClient.refreshAuctionsStatus();
                    auction = bidClient.findAuctionById(auction.getAuctionId()).orElse(auction);
                    return FXCollections.observableArrayList(
                            bidClient.getAuctionBidHistory(String.valueOf(auction.getAuctionId())));
                } catch (RuntimeException e) {
                    return null;
                }
            }
        };

        task.setOnSucceeded(event -> {
            ObservableList<BidTransaction> rows = task.getValue();
            if (rows == null) {
                completeRefreshCycle();
                return;
            }
            String historySignature = buildHistorySignature(rows);
            if (!historySignature.equals(lastHistorySignature)) {
                historyRows.setAll(rows);
                renderChart(rows);
                lastHistorySignature = historySignature;
            }
            updateHeader();
            updateBidPanelState();
            completeRefreshCycle();
        });
        task.setOnFailed(event -> showError(
                task.getException() == null ? "Không thể cập nhật dữ liệu." : task.getException().getMessage()));
        executor.submit(task);
    }

    private void completeRefreshCycle() {
        refreshInProgress = false;
        if (refreshQueued) {
            refreshQueued = false;
            refreshDataAsync();
        }
    }

    private String buildHistorySignature(List<BidTransaction> rows) {
        if (rows == null || rows.isEmpty()) {
            return "0";
        }
        BidTransaction first = rows.get(0);
        BidTransaction last = rows.get(rows.size() - 1);
        return rows.size()
                + "|" + first.getBidderId() + "|" + first.getBidAmount() + "|" + String.valueOf(first.getBidTime())
                + "|" + last.getBidderId() + "|" + last.getBidAmount() + "|" + String.valueOf(last.getBidTime());
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
            lblProductType.setText(auction.getItem().getClass_SimpleName());
            lblSellerId.setText(auction.getSellerId());
            lblStartPrice.setText(FormatUtils.formatCurrency(auction.getItem().getStartingPrice()));
            txtDescription
                    .setText(auction.getItem().getDescription() == null ? "" : auction.getItem().getDescription());
            currentProductImageUrl = auction.getItem().getImageUrl();
            loadProductImage(currentProductImageUrl);
        } else {
            lblProductName.setText("-");
            lblProductType.setText("-");
            lblSellerId.setText("-");
            lblStartPrice.setText("-");
            txtDescription.setText("");
            currentProductImageUrl = null;
            imgProduct.setImage(null);
        }
    }

    private void loadProductImage(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            imgProduct.setImage(null);
            updateImageInteractionState(false);
            return;
        }
        // Load image in background task to avoid blocking the UI
        Task<Image> loadImageTask = new Task<>() {
            @Override
            protected Image call() {
                try {
                    return new Image(imageUrl, 200, 150, true, true, true);
                } catch (Exception e) {
                    return null;
                }
            }
        };
        loadImageTask.setOnSucceeded(event -> {
            Image result = loadImageTask.getValue();
            if (result != null && !result.isError()) {
                imgProduct.setImage(result);
                updateImageInteractionState(true);
            } else {
                imgProduct.setImage(null);
                updateImageInteractionState(false);
            }
        });
        loadImageTask.setOnFailed(event -> {
            imgProduct.setImage(null);
            updateImageInteractionState(false);
        });
        new Thread(loadImageTask).start();
    }

    private void updateImageInteractionState(boolean hasImage) {
        imgProduct.setCursor(hasImage ? Cursor.HAND : Cursor.DEFAULT);
        imgProduct.setOpacity(hasImage ? 1.0 : 0.65);
    }

    private void openImagePreview() {
        if (currentProductImageUrl == null || currentProductImageUrl.isBlank()) {
            return;
        }
        ImageView previewImageView;
        Label hintLabel;

        if (imagePreviewStage != null && imagePreviewStage.isShowing()) {
            previewImageView = (ImageView) imagePreviewStage.getScene().getUserData();
            hintLabel = (Label) imagePreviewStage.getProperties().get("hintLabel");
            loadPreviewImage(previewImageView, hintLabel);
            imagePreviewStage.toFront();
            imagePreviewStage.requestFocus();
            return;
        }

        previewImageView = new ImageView();
        previewImageView.setSmooth(true);
        previewImageView.setPreserveRatio(true);

        hintLabel = new Label("Đang tải ảnh gốc...");
        hintLabel.setMouseTransparent(true);
        hintLabel.setStyle(
                "-fx-text-fill: white; -fx-font-size: 13px; -fx-padding: 10 14; -fx-background-color: rgba(17,24,39,0.72); -fx-background-radius: 999;");
        StackPane imageContainer = new StackPane(previewImageView);
        imageContainer.setAlignment(Pos.CENTER);
        imageContainer.setStyle("-fx-background-color: #111827;");

        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(imageContainer.widthProperty());
        clip.heightProperty().bind(imageContainer.heightProperty());
        imageContainer.setClip(clip);

        previewImageView.fitWidthProperty().bind(imageContainer.widthProperty());
        previewImageView.fitHeightProperty().bind(imageContainer.heightProperty());
        imageContainer.widthProperty().addListener((obs, oldValue, newValue) ->
                updatePreviewViewport(previewImageView, imageContainer));
        imageContainer.heightProperty().addListener((obs, oldValue, newValue) ->
                updatePreviewViewport(previewImageView, imageContainer));

        StackPane content = new StackPane(imageContainer, hintLabel);
        content.setAlignment(hintLabel, Pos.BOTTOM_CENTER);
        content.setStyle("-fx-background-color: #111827;");
        StackPane.setMargin(hintLabel, new javafx.geometry.Insets(0, 0, 18, 0));

        Scene scene = new Scene(content, 1000, 720, Color.web("#111827"));
        scene.setUserData(previewImageView);

        Stage stage = new Stage();
        stage.initModality(Modality.NONE);
        if (itemNameLabel.getScene() != null && itemNameLabel.getScene().getWindow() instanceof Stage ownerStage) {
            stage.initOwner(ownerStage);
        }
        stage.setTitle("Xem ảnh sản phẩm");
        stage.setScene(scene);
        stage.setMinWidth(320);
        stage.setMinHeight(240);
        stage.getProperties().put("hintLabel", hintLabel);
        stage.getProperties().put("imageContainer", imageContainer);
        stage.setOnHidden(event -> imagePreviewStage = null);

        imagePreviewStage = stage;
        stage.show();
        loadPreviewImage(previewImageView, hintLabel);
    }

    private void loadPreviewImage(ImageView previewImageView, Label hintLabel) {
        String imageUrl = currentProductImageUrl;
        if (imageUrl == null || imageUrl.isBlank()) {
            return;
        }

        hintLabel.setText("Đang tải ảnh gốc...");
        Task<Image> loadOriginalImageTask = new Task<>() {
            @Override
            protected Image call() {
                try {
                    return new Image(imageUrl, true);
                } catch (Exception e) {
                    return null;
                }
            }
        };
        loadOriginalImageTask.setOnSucceeded(event -> {
            Image image = loadOriginalImageTask.getValue();
            if (image == null || image.isError()) {
                previewImageView.setImage(null);
                previewImageView.setViewport(null);
                hintLabel.setText("Không tải được ảnh gốc.");
                return;
            }
            previewImageView.setImage(image);
            StackPane imageContainer = imagePreviewStage == null
                    ? null
                    : (StackPane) imagePreviewStage.getProperties().get("imageContainer");
            if (imageContainer != null) {
                updatePreviewViewport(previewImageView, imageContainer);
            }
            hintLabel.setText("Ảnh gốc - kéo cửa sổ để phóng to hoặc thu nhỏ");
        });
        loadOriginalImageTask.setOnFailed(event -> {
            previewImageView.setImage(null);
            previewImageView.setViewport(null);
            hintLabel.setText("Không tải được ảnh gốc.");
        });
        new Thread(loadOriginalImageTask).start();
    }

    private void updatePreviewViewport(ImageView previewImageView, StackPane imageContainer) {
        Image image = previewImageView.getImage();
        if (image == null || image.isError() || imageContainer == null) {
            previewImageView.setViewport(null);
            return;
        }

        double containerWidth = imageContainer.getWidth();
        double containerHeight = imageContainer.getHeight();
        if (containerWidth <= 0 || containerHeight <= 0 || image.getWidth() <= 0 || image.getHeight() <= 0) {
            previewImageView.setViewport(null);
            return;
        }

        double imageAspect = image.getWidth() / image.getHeight();
        double containerAspect = containerWidth / containerHeight;

        if (imageAspect > containerAspect) {
            double viewportWidth = image.getHeight() * containerAspect;
            double x = (image.getWidth() - viewportWidth) / 2.0;
            previewImageView.setViewport(new Rectangle2D(x, 0, viewportWidth, image.getHeight()));
            return;
        }

        double viewportHeight = image.getWidth() / containerAspect;
        double y = (image.getHeight() - viewportHeight) / 2.0;
        previewImageView.setViewport(new Rectangle2D(0, y, image.getWidth(), viewportHeight));
    }

    private void updateBidPanelState() {
        if (viewOnly) {
            setBidPanelVisible(false);
            hideErrorIfDisplayTimeElapsed();
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
        hideErrorIfDisplayTimeElapsed();
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
            errorVisibleSinceMillis = System.currentTimeMillis();
        });
    }

    private void hideError() {
        errorLabel.setVisible(false);
        errorLabel.setText("");
        errorVisibleSinceMillis = 0L;
    }

    private void hideErrorIfDisplayTimeElapsed() {
        if (errorLabel == null || !errorLabel.isVisible()) {
            return;
        }
        long elapsed = System.currentTimeMillis() - errorVisibleSinceMillis;
        if (elapsed < ERROR_MIN_DISPLAY_MILLIS) {
            return;
        }
        hideError();
    }

    private boolean isOwnAuction() {
        if (auction == null || currentBidder == null || auction.getSellerId() == null) {
            return false;
        }
        return auction.getSellerId().trim().equals(String.valueOf(currentBidder.getId()));
    }

    private void shutdown() {
        if (imagePreviewStage != null) {
            try {
                imagePreviewStage.close();
            } catch (RuntimeException ignored) {
            }
            imagePreviewStage = null;
        }
        try {
            ClientSession.getSocket().removePushListener(auctionPushListener);
        } catch (RuntimeException ignored) {
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
