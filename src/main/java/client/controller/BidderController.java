package client.controller;

import client.application.ClientSession;
import client.application.DashboardNavigator;
import client.network.AuthClient;
import client.network.BidClient;
import common.models.auction.Auction;
import common.models.auction.BidTransaction;
import common.models.user.Bidder;
import common.models.user.Seller;
import common.models.user.User;
import common.utils.FormatUtils;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BidderController {
    @FXML private Label lblWalletBalance;
    @FXML private Label lblBidderName;
    @FXML private TextField txtSearch;
    @FXML private TextField txtSearch1;
    @FXML private ComboBox<String> cbStatusFilter;
    @FXML private ComboBox<String> cbSortBy;
    @FXML private TableView<AuctionRow> tblAuctions;
    @FXML private TableColumn<AuctionRow, String> colId;
    @FXML private TableColumn<AuctionRow, String> colItem;
    @FXML private TableColumn<AuctionRow, String> colType;
    @FXML private TableColumn<AuctionRow, String> colCurrentPrice;
    @FXML private TableColumn<AuctionRow, String> colSeller;
    @FXML private TableColumn<AuctionRow, String> colStatus;
    @FXML private TableColumn<AuctionRow, String> colEndTime;
    @FXML private TextField txtBidAmount;
    @FXML private TableView<BidHistoryRow> tblBidHistory;
    @FXML private TableColumn<BidHistoryRow, String> colHistoryId;
    @FXML private TableColumn<BidHistoryRow, String> colHistoryItem;
    @FXML private TableColumn<BidHistoryRow, String> colHistoryBid;
    @FXML private TableColumn<BidHistoryRow, String> colHistoryTime;
    @FXML private TableColumn<BidHistoryRow, String> colHistoryResult;
    @FXML private Label lblBalance;
    @FXML private Label lblTotalWon;
    @FXML private TextField txtDepositAmount;
    @FXML private LineChart<Number, Number> priceHistoryChart;
    @FXML private NumberAxis xAxis;
    @FXML private NumberAxis yAxis;

    private final BidClient bidClient = new BidClient();
    private final AuthClient authClient = new AuthClient();
    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();
    private final ObservableList<AuctionRow> auctionRows = FXCollections.observableArrayList();
    private final ObservableList<BidHistoryRow> bidHistoryRows = FXCollections.observableArrayList();
    private Bidder currentBidder;
    // Cache danh sach auction tu server de tra cuu
    private List<Auction> cachedAuctions = List.of();

    @FXML
    private void initialize() {
        if (!initializeCurrentBidder()) {
            return;
        }
        setupHeader();
        setupFilters();
        setupAuctionTable();
        setupHistoryTable();
        setupPriceChart();
        setupEnterActions();
        loadDashboardDataAsync();
        Platform.runLater(() -> {
            if (lblBidderName.getScene() != null && lblBidderName.getScene().getWindow() != null) {
                lblBidderName.getScene().getWindow().addEventHandler(WindowEvent.WINDOW_HIDDEN, e -> shutdown());
            }
        });
    }

    private boolean initializeCurrentBidder() {
        User user = ClientSession.getCurrentUser();
        if (user == null) {
            Platform.runLater(() -> redirectToLogin("Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại."));
            return false;
        }
        if (user instanceof Bidder bidder) {
            currentBidder = bidder;
        } else {
            currentBidder = new Bidder(user.getId(), user.getUsername(), user.getEmail(), user.getPassword());
            if (user instanceof Seller seller) {
                currentBidder.getWallet().setBalance(seller.getWallet().getBalance());
            }
        }
        ClientSession.setCurrentUser(currentBidder);
        return true;
    }

    @FXML
    private void handleSwitchToSeller() {
        if (!ensureBidderCanContinue()) return;
        try {
            User switched = authClient.switchRole(currentBidder.getId(), "seller");
            ClientSession.setCurrentUser(switched);
            Stage stage = (Stage) lblBidderName.getScene().getWindow();
            DashboardNavigator.showSellerDashboard(stage);
            shutdown();
        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Lỗi", "Không mở được seller dashboard: " + e.getMessage());
        } catch (RuntimeException e) {
            showAlert(Alert.AlertType.ERROR, "Lỗi", "Không đổi được role sang seller: " + e.getMessage());
        }
    }

    @FXML
    private void handleRefreshAuctions() {
        if (!ensureBidderCanContinue()) return;
        loadAuctionsAsync();
    }

    @FXML
    private void handleRefreshHistory() {
        if (!ensureBidderCanContinue()) return;
        loadBidHistoryAsync();
    }

    @FXML
    private void handleSearchAuctions() {
        if (!ensureBidderCanContinue()) return;
        loadAuctionsAsync();
    }

    @FXML
    private void handleSearchHistory() {
        if (!ensureBidderCanContinue()) return;
        loadBidHistoryAsync();
    }

    @FXML
    private void handlePlaceBid() {
        if (!ensureBidderCanContinue()) return;

        AuctionRow selectedRow = tblAuctions.getSelectionModel().getSelectedItem();
        if (selectedRow == null) {
            showAlert(Alert.AlertType.WARNING, "Cảnh báo", "Vui lòng chọn một phiên đấu giá.");
            return;
        }
        if (isOwnAuction(selectedRow.auction)) {
            showAlert(Alert.AlertType.WARNING, "Không hợp lệ", "Bạn không thể tự đấu giá sản phẩm của chính mình.");
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(txtBidAmount.getText().trim());
        } catch (NumberFormatException e) {
            showAlert(Alert.AlertType.ERROR, "Lỗi dữ liệu", "Giá đặt phải là số.");
            return;
        }
        if (amount <= 0) {
            showAlert(Alert.AlertType.ERROR, "Lỗi dữ liệu", "Giá đặt phải lớn hơn 0.");
            return;
        }

        runInBackground(() -> {
            bidClient.placeBid(selectedRow.id, currentBidder, amount);
            return null;
        }, () -> {
            txtBidAmount.clear();
            loadDashboardDataAsync();
            showAlert(Alert.AlertType.INFORMATION, "Thành công", "Đặt giá thành công.");
        });
    }

    @FXML
    private void handleViewDetails() {
        if (!ensureBidderCanContinue()) return;

        AuctionRow selectedRow = tblAuctions.getSelectionModel().getSelectedItem();
        if (selectedRow == null) {
            showAlert(Alert.AlertType.WARNING, "Cảnh báo", "Vui lòng chọn một phiên đấu giá.");
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/AuctionDetailsView.fxml"));
            Parent root = loader.load();
            AuctionDetailController controller = loader.getController();
            controller.setViewOnly(false);
            controller.setAuction(selectedRow.auction, currentBidder);

            Stage stage = new Stage();
            stage.setTitle("Chi tiết phiên đấu giá");
            stage.setScene(new Scene(root));
            AuctionDetailController.registerStage(stage);
            stage.show();
        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Lỗi hệ thống", "Không mở được màn hình chi tiết: " + e.getMessage());
        }
    }

    @FXML
    private void handleDeposit() {
        if (!ensureBidderCanContinue()) return;

        double amount;
        try {
            amount = Double.parseDouble(txtDepositAmount.getText().trim());
        } catch (NumberFormatException e) {
            showAlert(Alert.AlertType.ERROR, "Lỗi dữ liệu", "Số tiền nạp phải là số.");
            return;
        }
        if (amount <= 0) {
            showAlert(Alert.AlertType.ERROR, "Lỗi dữ liệu", "Số tiền nạp phải lớn hơn 0.");
            return;
        }

        runInBackground(() -> {
            bidClient.deposit(currentBidder.getId(), amount);
            return null;
        }, () -> {
            txtDepositAmount.clear();
            // Refresh wallet
            syncBidderState();
            updateWalletLabels();
        });
    }

    private void setupHeader() {
        lblBidderName.setText("Bidder: " + currentBidder.getUsername());
        updateWalletLabels();
    }

    private void updateWalletLabels() {
        String text = formatCurrency(currentBidder.getWallet().getBalance());
        lblWalletBalance.setText("Ví: " + text);
        lblBalance.setText(text);
    }

    private void setupFilters() {
        cbStatusFilter.getItems().addAll("Tất cả", "OPEN", "RUNNING", "FINISHED", "PAID", "CANCELED");
        cbStatusFilter.setValue("Tất cả");
        cbSortBy.getItems().addAll("Mới nhất", "Giá cao nhất", "Giá thấp nhất", "Sắp kết thúc");
        cbSortBy.setValue("Mới nhất");
        cbStatusFilter.setOnAction(event -> loadAuctionsAsync());
        cbSortBy.setOnAction(event -> loadAuctionsAsync());
    }

    private void setupAuctionTable() {
        colId.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().id));
        colItem.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().itemName));
        colType.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().type));
        colCurrentPrice.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().currentPrice));
        colSeller.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().seller));
        colStatus.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().status));
        colEndTime.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().endTime));
        tblAuctions.setItems(auctionRows);
        tblAuctions.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldValue, newValue) -> updateChart(newValue));
    }

    private void setupHistoryTable() {
        colHistoryId.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().auctionId));
        colHistoryItem.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().itemName));
        colHistoryBid.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().bidAmount));
        colHistoryTime.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().bidTime));
        colHistoryResult.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().result));
        tblBidHistory.setItems(bidHistoryRows);
    }

    private void setupPriceChart() {
        if (xAxis != null) xAxis.setLabel("Lần đặt giá");
        if (yAxis != null) yAxis.setLabel("Giá (VND)");
    }

    private void setupEnterActions() {
        txtSearch.setOnAction(event -> handleSearchAuctions());
        txtSearch1.setOnAction(event -> handleSearchHistory());
        txtBidAmount.setOnAction(event -> handlePlaceBid());
        txtDepositAmount.setOnAction(event -> handleDeposit());
    }

    private void loadDashboardDataAsync() {
        loadAuctionsAsync();
        loadBidHistoryAsync();
        updateWalletLabels();
    }

    @SuppressWarnings("unchecked")
    private void loadAuctionsAsync() {
        String keyword = txtSearch.getText() == null ? "" : txtSearch.getText().trim().toLowerCase(Locale.ROOT);
        String status = cbStatusFilter.getValue();
        String sort = cbSortBy.getValue();

        runInBackground(() -> {
            bidClient.refreshAuctionsStatus();
            List<Auction> auctions = bidClient.getAllAuctions();
            cachedAuctions = auctions;
            return filterAndSortAuctions(auctions, keyword, status, sort);
        }, rows -> {
            auctionRows.setAll(rows);
            if (!rows.isEmpty()) {
                tblAuctions.getSelectionModel().selectFirst();
                updateChart(rows.get(0));
            } else {
                clearChart();
            }
        });
    }

    private void loadBidHistoryAsync() {
        String keyword = txtSearch1.getText() == null ? "" : txtSearch1.getText().trim().toLowerCase(Locale.ROOT);

        runInBackground(() -> {
            List<BidTransaction> bids = bidClient.getBidderBidHistory(String.valueOf(currentBidder.getId()));
            return bids.stream()
                    .sorted(Comparator.comparing(BidTransaction::getBidTime, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                    .map(this::toBidHistoryRow)
                    .filter(row -> matchesHistoryKeyword(row, keyword))
                    .toList();
        }, rows -> {
            bidHistoryRows.setAll(rows);
            long wonCount = rows.stream().filter(r -> "Đang dẫn đầu".equals(r.result) || "Đã thắng".equals(r.result)).count();
            lblTotalWon.setText(wonCount + " phiên");
        });
    }

    private List<AuctionRow> filterAndSortAuctions(List<Auction> auctions, String keyword, String status, String sort) {
        return auctions.stream()
                .filter(a -> a.getItem() != null)
                .filter(a -> keyword.isEmpty()
                        || String.valueOf(a.getAuctionId()).contains(keyword)
                        || a.getItem().getName().toLowerCase(Locale.ROOT).contains(keyword)
                        || (a.getSellerId() != null && a.getSellerId().toLowerCase(Locale.ROOT).contains(keyword)))
                .filter(a -> "Tất cả".equals(status) || (a.getStatus() != null && a.getStatus().name().equalsIgnoreCase(status)))
                .sorted(resolveComparator(sort))
                .map(this::toAuctionRow)
                .toList();
    }

    private Comparator<Auction> resolveComparator(String sort) {
        if ("Giá cao nhất".equals(sort)) return Comparator.comparingDouble(Auction::getCurrentHighestBid).reversed();
        if ("Giá thấp nhất".equals(sort)) return Comparator.comparingDouble(Auction::getCurrentHighestBid);
        if ("Sắp kết thúc".equals(sort)) return Comparator.comparing(Auction::getEndTime, Comparator.nullsLast(Comparator.naturalOrder()));
        return Comparator.comparing(Auction::getStartTime, Comparator.nullsLast(Comparator.naturalOrder())).reversed();
    }

    private AuctionRow toAuctionRow(Auction a) {
        return new AuctionRow(
                String.valueOf(a.getAuctionId()),
                a.getItem() == null ? "-" : a.getItem().getName(),
                a.getItem() == null ? "-" : a.getItem().getClass().getSimpleName(),
                formatCurrency(a.getCurrentHighestBid()),
                a.getSellerId() != null ? a.getSellerId() : "-",
                a.getStatus() == null ? "-" : a.getStatus().name(),
                FormatUtils.formatDateTime(a.getEndTime()),
                a);
    }

    private BidHistoryRow toBidHistoryRow(BidTransaction bid) {
        Auction auction = findAuctionSafe(bid.getAuctionId());
        String result = resolveBidResult(auction, bid);
        return new BidHistoryRow(
                String.valueOf(bid.getAuctionId()),
                auction == null || auction.getItem() == null ? "-" : auction.getItem().getName(),
                formatCurrency(bid.getBidAmount()),
                FormatUtils.formatDateTime(bid.getBidTime()),
                result);
    }

    private String resolveBidResult(Auction auction, BidTransaction bid) {
        if (auction == null || auction.getCurrentLeaderId() == null || auction.getCurrentLeaderId() != bid.getBidderId()) {
            return "Đã bị vượt";
        }
        if (auction.getStatus() == common.models.auction.AuctionStatus.FINISHED
                || auction.getStatus() == common.models.auction.AuctionStatus.PAID) {
            return "Đã thắng";
        }
        if (auction.getStatus() == common.models.auction.AuctionStatus.CANCELED) {
            return "Phiên đã hủy";
        }
        return "Đang dẫn đầu";
    }

    private boolean matchesHistoryKeyword(BidHistoryRow row, String keyword) {
        return keyword.isEmpty()
                || row.auctionId.contains(keyword)
                || row.itemName.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private Auction findAuctionSafe(int auctionId) {
        // Tim tu cache truoc
        for (Auction a : cachedAuctions) {
            if (a.getAuctionId() == auctionId) return a;
        }
        // Neu khong co thi goi server
        try {
            return bidClient.findAuctionById(auctionId).orElse(null);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private void updateChart(AuctionRow row) {
        if (row == null || priceHistoryChart == null) {
            clearChart();
            return;
        }
        runInBackground(() -> bidClient.getAuctionBidHistory(row.id), bids -> {
            XYChart.Series<Number, Number> series = new XYChart.Series<>();
            series.setName("Giá đặt");
            List<BidTransaction> sorted = bids.stream()
                    .sorted(Comparator.comparing(BidTransaction::getBidTime, Comparator.nullsLast(Comparator.naturalOrder())))
                    .toList();
            int index = 1;
            for (BidTransaction bid : sorted) {
                series.getData().add(new XYChart.Data<>(index++, bid.getBidAmount()));
            }
            priceHistoryChart.getData().setAll(series);
        });
    }

    private void clearChart() {
        if (priceHistoryChart != null) priceHistoryChart.getData().clear();
    }

    private <T> void runInBackground(BackgroundSupplier<T> supplier, java.util.function.Consumer<T> onSuccess) {
        Task<T> task = new Task<>() {
            @Override
            protected T call() { return supplier.get(); }
        };
        task.setOnSucceeded(event -> onSuccess.accept(task.getValue()));
        task.setOnFailed(event -> Platform.runLater(() ->
                showAlert(Alert.AlertType.ERROR, "Lỗi hệ thống",
                        task.getException() == null ? "Không thể tải dữ liệu." : task.getException().getMessage())));
        backgroundExecutor.submit(task);
    }

    private void runInBackground(BackgroundSupplier<Void> supplier, Runnable onSuccess) {
        runInBackground(supplier, unused -> onSuccess.run());
    }

    private String formatCurrency(double amount) {
        return FormatUtils.formatCurrency(amount);
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    @FXML
    private void handleLogout() {
        AuctionDetailController.closeAllWindows();
        shutdown();
        try {
            authClient.logout();
        } catch (Exception ignored) {}
        ClientSession.clear();
        try {
            showLoginScreen();
        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Lỗi", "Không mở được màn hình đăng nhập: " + e.getMessage());
        }
    }

    private boolean ensureBidderCanContinue() {
        User currentUser = ClientSession.getCurrentUser();
        if (currentUser == null) {
            return redirectToLogin("Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.");
        }
        try {
            User latestUser = authClient.getUserById(currentUser.getId()).orElse(null);
            if (latestUser == null) {
                return redirectToLogin("Tài khoản không còn tồn tại. Vui lòng đăng nhập lại.");
            }
            if (latestUser.getStatus() == common.models.user.UserStatus.BANNED) {
                return redirectToLogin("Tài khoản của bạn đã bị khóa bởi admin.");
            }
            syncBidderState(latestUser);
            return true;
        } catch (RuntimeException e) {
            showAlert(Alert.AlertType.ERROR, "Lỗi", e.getMessage());
            return false;
        }
    }

    private boolean redirectToLogin(String message) {
        try {
            ClientSession.clear();
            showAlert(Alert.AlertType.WARNING, "Thông báo", message);
            showLoginScreen();
        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Lỗi", "Không mở được màn hình đăng nhập: " + e.getMessage());
        }
        return false;
    }

    private void showLoginScreen() throws IOException {
        Stage stage = (Stage) lblBidderName.getScene().getWindow();
        DashboardNavigator.showLogin(stage);
    }

    private void shutdown() {
        backgroundExecutor.shutdownNow();
    }

    private void syncBidderState() {
        try {
            User latestUser = authClient.getUserById(currentBidder.getId()).orElse(null);
            if (latestUser != null) syncBidderState(latestUser);
        } catch (RuntimeException ignored) {}
    }

    private void syncBidderState(User latestUser) {
        currentBidder.setUsername(latestUser.getUsername());
        currentBidder.setPassword(latestUser.getPassword());
        currentBidder.setStatus(latestUser.getStatus());
        if (latestUser instanceof Bidder bidder) {
            currentBidder.getWallet().setBalance(bidder.getWallet().getBalance());
        } else if (latestUser instanceof Seller seller) {
            currentBidder.getWallet().setBalance(seller.getWallet().getBalance());
        }
        ClientSession.setCurrentUser(currentBidder);
    }

    private boolean isOwnAuction(Auction auction) {
        if (auction == null || currentBidder == null || auction.getSellerId() == null) return false;
        return auction.getSellerId().trim().equals(String.valueOf(currentBidder.getId()));
    }

    @FunctionalInterface
    private interface BackgroundSupplier<T> {
        T get();
    }

    public static class AuctionRow {
        public final String id;
        public final String itemName;
        public final String type;
        public final String currentPrice;
        public final String seller;
        public final String status;
        public final String endTime;
        public final Auction auction;

        public AuctionRow(String id, String itemName, String type, String currentPrice, String seller, String status, String endTime, Auction auction) {
            this.id = id;
            this.itemName = itemName;
            this.type = type;
            this.currentPrice = currentPrice;
            this.seller = seller;
            this.status = status;
            this.endTime = endTime;
            this.auction = auction;
        }
    }

    public static class BidHistoryRow {
        public final String auctionId;
        public final String itemName;
        public final String bidAmount;
        public final String bidTime;
        public final String result;

        public BidHistoryRow(String auctionId, String itemName, String bidAmount, String bidTime, String result) {
            this.auctionId = auctionId;
            this.itemName = itemName;
            this.bidAmount = bidAmount;
            this.bidTime = bidTime;
            this.result = result;
        }
    }
}