package client.controller;

import common.models.auction.Auction;
import common.models.auction.BidTransaction;
import common.models.user.Bidder;
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
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.WindowEvent;
import javafx.util.Duration;
import server.repository.AuctionDAO;
import server.service.AuctionService;
import server.service.BidService;
import server.service.ItemService;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AuctionDetailController {
    @FXML private Label itemNameLabel;
    @FXML private Label statusLabel;
    @FXML private Label countdownLabel;
    @FXML private Label currentBidLabel;
    @FXML private Label lblProductName;
    @FXML private Label lblProductType;
    @FXML private Label lblSellerId;
    @FXML private Label lblStartPrice;
    @FXML private TextArea txtDescription;
    @FXML private TextField bidAmountField;
    @FXML private Label errorLabel;
    @FXML private VBox bidPanel;
    @FXML private LineChart<Number, Number> bidChart;
    @FXML private TableView<BidTransaction> historyTable;
    @FXML private TableColumn<BidTransaction, String> bidderCol;
    @FXML private TableColumn<BidTransaction, String> amountCol;
    @FXML private TableColumn<BidTransaction, String> timeCol;

    private final BidService bidService = new BidService();
    private final AuctionService auctionService = new AuctionService(new ItemService());
    private final AuctionDAO auctionDAO = new AuctionDAO();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private final ObservableList<BidTransaction> historyRows = FXCollections.observableArrayList();
    private Timeline refreshTimeline;
    private Auction auction;
    private Bidder currentBidder;

    @FXML
    private void initialize() {
        bidderCol.setCellValueFactory(v -> new SimpleStringProperty(String.valueOf(v.getValue().getBidderId())));
        amountCol.setCellValueFactory(v -> new SimpleStringProperty(String.valueOf(v.getValue().getBidAmount())));
        timeCol.setCellValueFactory(v -> new SimpleStringProperty(
                v.getValue().getBidTime() == null ? "-" : v.getValue().getBidTime().format(timeFormatter)));
        historyTable.setItems(historyRows);
        Platform.runLater(() -> {
            if (itemNameLabel.getScene() != null && itemNameLabel.getScene().getWindow() != null) {
                itemNameLabel.getScene().getWindow().addEventHandler(WindowEvent.WINDOW_HIDDEN, e -> shutdown());
            }
        });
    }

    public void setAuction(Auction auction, Bidder bidder) {
        this.auction = auction;
        this.currentBidder = bidder;
        setBidPanelVisible(bidder != null);
        updateHeader();
        refreshDataAsync();
        startAutoRefresh();
    }

    private void setBidPanelVisible(boolean visible) {
        bidPanel.setVisible(visible);
        bidPanel.setManaged(visible);
    }

    @FXML
    private void handlePlaceBid() {
        if (auction == null || currentBidder == null) {
            showError("Khong the dat gia o thoi diem nay.");
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(bidAmountField.getText().trim());
        } catch (NumberFormatException e) {
            showError("So tien dat phai la so.");
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
        task.setOnFailed(event -> showError(task.getException() == null ? "Dat gia that bai." : task.getException().getMessage()));
        executor.submit(task);
    }

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
                        bidService.getAuctionBidHistory(String.valueOf(auction.getAuctionId())).stream()
                                .sorted(Comparator.comparing(BidTransaction::getBidTime, Comparator.nullsLast(Comparator.naturalOrder())))
                                .toList()
                );
            }
        };

        task.setOnSucceeded(event -> {
            ObservableList<BidTransaction> rows = task.getValue();
            historyRows.setAll(rows);
            renderChart(rows);
            updateHeader();
        });
        task.setOnFailed(event -> showError(task.getException() == null ? "Khong the cap nhat du lieu." : task.getException().getMessage()));
        executor.submit(task);
    }

    private void renderChart(ObservableList<BidTransaction> rows) {
        XYChart.Series<Number, Number> series = new XYChart.Series<>();
        series.setName("Gia dat");
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
        currentBidLabel.setText(String.format("%,.0f VND", auction.getCurrentHighestBid()));
        countdownLabel.setText("Thời gian còn lại: " + remainingTimeText());
        if (auction.getItem() != null) {
            lblProductName.setText(itemName);
            lblProductType.setText(auction.getItem().getClass().getSimpleName());
            lblSellerId.setText(auction.getSellerId());
            lblStartPrice.setText(String.format("%,.0f VND", auction.getItem().getStartingPrice()));
            txtDescription.setText(auction.getItem().getDescription() == null ? "" : auction.getItem().getDescription());
        } else {
            lblProductName.setText("-");
            lblProductType.setText("-");
            lblSellerId.setText("-");
            lblStartPrice.setText("-");
            txtDescription.setText("");
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

    private void shutdown() {
        if (refreshTimeline != null) {
            refreshTimeline.stop();
        }
        executor.shutdownNow();
    }
}
