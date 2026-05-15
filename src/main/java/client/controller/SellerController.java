package client.controller;

import client.application.ClientSession;
import client.application.DashboardNavigator;
import client.network.AuctionClient;
import client.network.AuctionClient.CreateAuctionRequest;
import common.models.auction.Auction;
import common.models.auction.AuctionStatus;
import common.models.user.User;
import common.models.user.UserStatus;
import common.utils.FormatUtils;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import server.repository.AuctionDAO;
import server.service.UserService;

import java.io.IOException;
import java.net.URL;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class SellerController implements Initializable {
    private final UserService userService = new UserService();
    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();

    @FXML
    private Label lblSellerName;
    @FXML
    private TextField txtItemName;
    @FXML
    private ComboBox<String> cbItemType;
    @FXML
    private TextField txtStartPrice;
    @FXML
    private TextField txtBuyNowPrice;
    @FXML
    private DatePicker dpEndDate;
    @FXML
    private ComboBox<String> cbEndHour;
    @FXML
    private ComboBox<String> cbEndMinute;
    @FXML
    private TextArea txtDescription;
    @FXML
    private VBox vboxDynamicFields;
    @FXML
    private Label lblDynamicTitle;
    @FXML
    private GridPane gridArt;
    @FXML
    private TextField txtArtist;
    @FXML
    private TextField txtArtYear;
    @FXML
    private TextField txtMaterial;
    @FXML
    private GridPane gridElectronics;
    @FXML
    private TextField txtBrand;
    @FXML
    private TextField txtModel;
    @FXML
    private TextField txtCondition;
    @FXML
    private GridPane gridVehicle;
    @FXML
    private TextField txtVehicleBrand;
    @FXML
    private TextField txtMileage;
    @FXML
    private TextField txtVehicleYear;
    @FXML
    private TextField txtAuctionSearch;
    @FXML
    private ComboBox<String> cbSortBy;
    @FXML
    private TableView<AuctionRow> tblMyAuctions;
    @FXML
    private TableColumn<AuctionRow, String> colAuctionId;
    @FXML
    private TableColumn<AuctionRow, String> colAuctionItem;
    @FXML
    private TableColumn<AuctionRow, String> colStartPrice;
    @FXML
    private TableColumn<AuctionRow, String> colCurrentPrice;
    @FXML
    private TableColumn<AuctionRow, String> colStatus;
    @FXML
    private TableColumn<AuctionRow, String> colEndTime;
    @FXML
    private Label lblTotalAuctions;
    @FXML
    private Label lblActiveAuctions;
    @FXML
    private Label lblTotalRevenue;

    private final AuctionClient auctionClient = new AuctionClient();
    private final AuctionDAO auctionDAO = new AuctionDAO();
    private String currentSellerId;
    private String currentSellerName;
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        if (!initializeSellerName()) {
            return;
        }
        cbItemType.getItems().addAll("Tác phẩm nghệ thuật", "Điện tử", "Phương tiện");
        cbSortBy.getItems().addAll("Mới nhất", "Cũ nhất", "Trạng thái", "Giá tăng", "Giá giảm");
        cbSortBy.setValue("Mới nhất");
        cbSortBy.setOnAction(event -> loadSellerDashboardData());
        cbItemType.setOnAction(event -> handleItemTypeChanged());
        configurePriceFields();
        configureEndTimeFields();
        colAuctionId.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().id));
        colAuctionItem.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().itemName));
        colStartPrice.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().startPrice));
        colCurrentPrice.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().currentPrice));
        colStatus.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().status));
        colEndTime.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().endTime));
        Platform.runLater(() -> {
            if (lblSellerName.getScene() != null && lblSellerName.getScene().getWindow() != null) {
                lblSellerName.getScene().getWindow().addEventHandler(javafx.stage.WindowEvent.WINDOW_HIDDEN,
                        event -> shutdown());
            }
        });
        loadSellerDashboardData();
    }

    private boolean initializeSellerName() {
        User user = ClientSession.getCurrentUser();
        if (user == null) {
            Platform.runLater(() -> redirectToLogin("Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại."));
            return false;
        }

        currentSellerId = String.valueOf(user.getId());
        currentSellerName = user.getUsername();
        lblSellerName.setText("Seller: " + currentSellerName);
        return true;
    }

    @FXML
    private void handleCreateAuction() {
        if (!ensureSellerCanContinue()) {
            return;
        }
        String validationError = getAuctionFormValidationError();
        if (validationError != null) {
            showAlert(Alert.AlertType.WARNING, "Cảnh báo", validationError);
            return;
        }
        try {
            Auction auction = auctionClient.createAuction(new CreateAuctionRequest(
                    currentSellerId,
                    txtItemName.getText().trim(),
                    cbItemType.getValue(),
                    Double.parseDouble(txtStartPrice.getText().trim()),
                    txtDescription.getText().trim(),
                    getSelectedEndTime(),
                    txtArtist.getText(),
                    txtArtYear.getText(),
                    txtMaterial.getText(),
                    txtBrand.getText(),
                    txtModel.getText(),
                    txtCondition.getText(),
                    txtVehicleBrand.getText(),
                    txtMileage.getText(),
                    txtVehicleYear.getText()));
            showAlert(Alert.AlertType.INFORMATION, "Thành công", "Đã tạo phiên: " + auction.getAuctionId());
            clearAuctionForm();
            loadSellerDashboardData();
        } catch (RuntimeException e) {
            showAlert(Alert.AlertType.ERROR, "Lỗi", e.getMessage());
        }
    }

    @FXML
    private void handleClearForm() {
        if (!ensureSellerCanContinue()) {
            return;
        }
        clearAuctionForm();
        hideTypeSpecificFields();
    }

    @FXML
    private void handleRefreshData() {
        if (!ensureSellerCanContinue()) {
            return;
        }
        loadSellerDashboardData();
    }

    @FXML
    private void handleSwitchToBidder() {
        if (!ensureSellerCanContinue()) {
            return;
        }

        try {
            User switchedUser = userService.switchRole(ClientSession.getCurrentUser(), "bidder");
            ClientSession.setCurrentUser(switchedUser);
            Stage stage = (Stage) lblSellerName.getScene().getWindow();
            DashboardNavigator.showBidderDashboard(stage);
            shutdown();
        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Lỗi", "Không mở được bidder dashboard: " + e.getMessage());
        } catch (RuntimeException e) {
            showAlert(Alert.AlertType.ERROR, "Lỗi", "Không đổi được role sang bidder: " + e.getMessage());
        }
    }

    @FXML
    private void handleEndAuction() {
        if (!ensureSellerCanContinue()) {
            return;
        }
        AuctionRow row = tblMyAuctions.getSelectionModel().getSelectedItem();
        if (row == null) {
            showAlert(Alert.AlertType.WARNING, "Cảnh báo", "Vui lòng chọn phiên.");
            return;
        }
        try {
            auctionClient.endAuction(currentSellerId, row.id);
            loadSellerDashboardData();
        } catch (RuntimeException e) {
            showAlert(Alert.AlertType.ERROR, "Lỗi", e.getMessage());
        }
    }

    @FXML
    private void handleViewAuctionDetails() {
        if (!ensureSellerCanContinue()) {
            return;
        }
        AuctionRow row = tblMyAuctions.getSelectionModel().getSelectedItem();
        if (row == null) {
            showAlert(Alert.AlertType.WARNING, "Cảnh báo", "Vui lòng chọn phiên.");
            return;
        }

        Auction auction = auctionDAO.findById(Integer.parseInt(row.id)).orElse(null);
        if (auction == null) {
            showAlert(Alert.AlertType.ERROR, "Lỗi", "Không tìm thấy phiên: " + row.id);
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/AuctionDetailsView.fxml"));
            Parent root = loader.load();
            AuctionDetailController controller = loader.getController();
            controller.setViewOnly(true);
            controller.setAuction(auction, null);
            controller.setAuction(auction, null);

            Stage stage = new Stage();
            stage.setTitle("Chi tiết phiên đấu giá");
            stage.setScene(new Scene(root));
            AuctionDetailController.registerStage(stage);
            stage.show();

        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Lỗi", "Không mở được màn hình chi tiết: " + e.getMessage());
        }
    }

    @FXML
    private void handleLogout() throws IOException {
        AuctionDetailController.closeAllWindows();
        userService.logout();
        ClientSession.clear();
        showLoginScreen();
        showAlert(Alert.AlertType.INFORMATION, "Thông báo", "Đã đăng xuất.");
    }

    private boolean ensureSellerCanContinue() {
        User currentUser = ClientSession.getCurrentUser();
        if (currentUser == null) {
            return redirectToLogin("Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.");
        }

        try {
            User latestUser = userService.findById(currentUser.getId()).orElse(null);
            if (latestUser == null) {
                return redirectToLogin("Tài khoản không còn tồn tại. Vui lòng đăng nhập lại.");
            }

            if (latestUser.getStatus() == UserStatus.BANNED) {
                return redirectToLogin("Tài khoản của bạn đã bị khóa bởi admin.");
            }

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
        Stage stage = (Stage) lblSellerName.getScene().getWindow();
        DashboardNavigator.showLogin(stage);
    }

    private void handleItemTypeChanged() {
        String type = cbItemType.getValue();
        if (type == null) {
            hideTypeSpecificFields();
            return;
        }
        vboxDynamicFields.setManaged(true);
        hideTypeSpecificFields();
        vboxDynamicFields.setVisible(true);

        switch (type) {
            case "Tác phẩm nghệ thuật" -> {
                lblDynamicTitle.setVisible(true);
                lblDynamicTitle.setText("Thông tin tác phẩm nghệ thuật");
                gridArt.setVisible(true);
                gridArt.setManaged(true);
            }
            case "Điện tử" -> {
                lblDynamicTitle.setVisible(true);
                lblDynamicTitle.setText("Thông tin sản phẩm điện tử");
                gridElectronics.setVisible(true);
                gridElectronics.setManaged(true);
            }
            case "Phương tiện" -> {
                lblDynamicTitle.setVisible(true);
                lblDynamicTitle.setText("Thông tin phương tiện");
                gridVehicle.setVisible(true);
                gridVehicle.setManaged(true);
            }
        }
    }

    private void hideTypeSpecificFields() {
        vboxDynamicFields.setVisible(false);
        lblDynamicTitle.setVisible(false);
        gridArt.setVisible(false);
        gridArt.setManaged(false);
        gridElectronics.setVisible(false);
        gridElectronics.setManaged(false);
        gridVehicle.setVisible(false);
        gridVehicle.setManaged(false);
    }

    private String getAuctionFormValidationError() {
        if (!hasText(txtItemName.getText())) {
            return "Vui lòng nhập tên sản phẩm.";
        }
        if (cbItemType.getValue() == null) {
            return "Vui lòng chọn loại sản phẩm.";
        }
        if (!hasText(txtDescription.getText())) {
            return "Vui lòng nhập mô tả sản phẩm.";
        }
        try {
            double p = Double.parseDouble(txtStartPrice.getText().trim());
            if (p <= 0) {
                return "Giá khởi điểm phải lớn hơn 0.";
            }
        } catch (Exception e) {
            return "Giá khởi điểm phải là số hợp lệ.";
        }
        try {
            LocalDateTime end = getSelectedEndTime();
            if (!LocalDateTime.now().isBefore(end)) {
                return "Thời gian kết thúc phải sau hiện tại.";
            }
        } catch (Exception e) {
            return e.getMessage();
        }
        String itemType = cbItemType.getValue().toLowerCase(Locale.ROOT);
        if (itemType.contains("ngh") && !hasText(txtArtist.getText())) {
            return "Vui lòng nhập tên nghệ sĩ.";
        }
        if ((itemType.contains("phương") || itemType.contains("phuong") || itemType.contains("vehicle"))
                && !isNonNegativeInteger(txtMileage.getText())) {
            return "Số km đã đi phải là số nguyên lớn hơn hoặc bằng 0.";
        }
        return null;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private boolean isNonNegativeInteger(String value) {
        if (!hasText(value)) {
            return false;
        }
        try {
            return Integer.parseInt(value.trim()) >= 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private void clearAuctionForm() {
        txtItemName.clear();
        cbItemType.setValue(null);
        txtStartPrice.clear();
        txtBuyNowPrice.clear();
        dpEndDate.setValue(LocalDate.now().plusDays(1));
        cbEndHour.setValue("23");
        cbEndMinute.setValue("59");
        txtDescription.clear();
        txtArtist.clear();
        txtArtYear.clear();
        txtMaterial.clear();
        txtBrand.clear();
        txtModel.clear();
        txtCondition.clear();
        txtVehicleBrand.clear();
        txtMileage.clear();
        txtVehicleYear.clear();
    }

    private void configurePriceFields() {
        txtStartPrice.setTextFormatter(
                new TextFormatter<>(change -> change.getControlNewText().matches("\\d*") ? change : null));
        txtBuyNowPrice.setTextFormatter(
                new TextFormatter<>(change -> change.getControlNewText().matches("\\d*") ? change : null));
    }

    private void configureEndTimeFields() {
        dpEndDate.setConverter(new StringConverter<>() {
            @Override
            public String toString(LocalDate date) {
                return date == null ? "" : dateFormatter.format(date);
            }

            @Override
            public LocalDate fromString(String value) {
                if (value == null || value.trim().isEmpty()) {
                    return null;
                }
                try {
                    return LocalDate.parse(value.trim(), dateFormatter);
                } catch (DateTimeParseException e) {
                    throw new IllegalArgumentException("Ngày kết thúc phải có dạng dd/MM/yyyy.");
                }
            }
        });

        for (int hour = 0; hour < 24; hour++) {
            cbEndHour.getItems().add(String.format("%02d", hour));
        }
        for (int minute = 0; minute < 60; minute++) {
            cbEndMinute.getItems().add(String.format("%02d", minute));
        }
        dpEndDate.setValue(LocalDate.now().plusDays(1));
        cbEndHour.setValue("23");
        cbEndMinute.setValue("59");
    }

    private LocalDateTime getSelectedEndTime() {
        LocalDate date = dpEndDate.getValue();
        String hour = cbEndHour.getValue();
        String minute = cbEndMinute.getValue();
        if (date == null || hour == null || minute == null) {
            throw new IllegalArgumentException("Vui lòng chọn đầy đủ ngày, giờ và phút kết thúc.");
        }
        return date.atTime(Integer.parseInt(hour), Integer.parseInt(minute));
    }

    private void loadSellerDashboardData() {
        String keyword = txtAuctionSearch.getText() == null
                ? ""
                : txtAuctionSearch.getText().trim().toLowerCase();
        String sortBy = cbSortBy.getValue();

        Task<SellerDashboardData> task = new Task<>() {
            @Override
            protected SellerDashboardData call() {
                List<Auction> sellerAuctions = auctionDAO.findBySellerId(currentSellerId);
                List<AuctionRow> rows = sellerAuctions.stream()
                        .filter(auction -> matchesAuctionKeyword(auction, keyword))
                        .sorted(resolveAuctionComparator(sortBy))
                        .map(SellerController.this::toAuctionRow)
                        .collect(Collectors.toList());

                long active = sellerAuctions.stream()
                        .filter(a -> a.getStatus() == AuctionStatus.RUNNING || a.getStatus() == AuctionStatus.OPEN)
                        .count();
                double revenue = sellerAuctions.stream()
                        .filter(a -> a.getStatus() == AuctionStatus.FINISHED || a.getStatus() == AuctionStatus.PAID)
                        .mapToDouble(Auction::getCurrentHighestBid)
                        .sum();

                return new SellerDashboardData(rows, sellerAuctions.size(), active, revenue);
            }
        };

        task.setOnSucceeded(event -> {
            SellerDashboardData data = task.getValue();
            tblMyAuctions.getItems().setAll(data.rows());
            lblTotalAuctions.setText(String.valueOf(data.totalAuctions()));
            lblActiveAuctions.setText(String.valueOf(data.activeAuctions()));
            lblTotalRevenue.setText(formatCurrency(data.totalRevenue()));
        });
        task.setOnFailed(event -> showAlert(Alert.AlertType.ERROR, "Lỗi", getTaskErrorMessage(task)));
        backgroundExecutor.submit(task);
    }

    private boolean matchesAuctionKeyword(Auction auction, String keyword) {
        if (keyword.isEmpty()) {
            return true;
        }

        String auctionId = String.valueOf(auction.getAuctionId());
        String itemName = auction.getItem() == null ? "" : auction.getItem().getName().toLowerCase();
        return auctionId.contains(keyword) || itemName.contains(keyword);
    }

    private AuctionRow toAuctionRow(Auction auction) {
        return new AuctionRow(
                String.valueOf(auction.getAuctionId()),
                auction.getItem() == null ? "-" : auction.getItem().getName(),
                auction.getItem() == null ? "-" : formatCurrency(auction.getItem().getStartingPrice()),
                formatCurrency(auction.getCurrentHighestBid()),
                auction.getStatus().name(),
                FormatUtils.formatDateTime(auction.getEndTime()));
    }

    private Comparator<Auction> resolveAuctionComparator() {
        return resolveAuctionComparator(cbSortBy.getValue());
    }

    private Comparator<Auction> resolveAuctionComparator(String sortBy) {
        return switch (sortBy) {
            case "Cũ nhất" ->
                Comparator.comparing(Auction::getStartTime, Comparator.nullsLast(Comparator.naturalOrder()));
            case "Trạng thái" -> Comparator.comparing(a -> a.getStatus().name());
            case "Giá tăng" -> Comparator.comparingDouble(Auction::getCurrentHighestBid);
            case "Giá giảm" -> Comparator.comparingDouble(Auction::getCurrentHighestBid).reversed();
            default ->
                Comparator.comparing(Auction::getStartTime, Comparator.nullsLast(Comparator.naturalOrder())).reversed();
        };
    }

    private String formatCurrency(double value) {
        return FormatUtils.formatCurrency(value);
    }

    private void showAlert(Alert.AlertType type, String title, String msg) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }

    private String getTaskErrorMessage(Task<?> task) {
        Throwable exception = task.getException();
        return exception == null ? "Không tải được dữ liệu." : exception.getMessage();
    }

    private void shutdown() {
        backgroundExecutor.shutdownNow();
    }

    private record SellerDashboardData(
            List<AuctionRow> rows,
            int totalAuctions,
            long activeAuctions,
            double totalRevenue) {
    }

    public static class AuctionRow {
        private final String id;
        private final String itemName;
        private final String startPrice;
        private final String currentPrice;
        private final String status;
        private final String endTime;

        public AuctionRow(String id, String itemName, String startPrice, String currentPrice, String status,
                String endTime) {
            this.id = id;
            this.itemName = itemName;
            this.startPrice = startPrice;
            this.currentPrice = currentPrice;
            this.status = status;
            this.endTime = endTime;
        }
    }
}
