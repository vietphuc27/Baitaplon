package client.controller;

import client.application.ClientSession;
import client.application.DashboardNavigator;
import client.network.AuthClient;
import client.network.BidClient;
import client.network.SellerClient;
import client.network.SellerClient.CreateAuctionRequest;
import common.models.auction.Auction;
import common.models.auction.AuctionStatus;
import common.models.item.Art;
import common.models.item.Electronics;
import common.models.item.Item;
import common.models.item.Vehicle;
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
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class SellerController implements Initializable {
    private static final long MAX_IMAGE_SIZE_BYTES = 15L * 1024 * 1024;

    private final AuthClient authClient = new AuthClient();
    private final SellerClient sellerClient = new SellerClient();
    private final BidClient bidClient = new BidClient();
    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();

    @FXML
    private Label lblSellerName;
    @FXML
    private TabPane sellerTabPane;
    @FXML
    private Tab tabCreateAuction;
    @FXML
    private Label lblAuctionFormTitle;
    @FXML
    private TextField txtItemName;
    @FXML
    private ComboBox<String> cbItemType;
    @FXML
    private TextField txtStartPrice;
    @FXML
    private DatePicker dpStartDate;
    @FXML
    private ComboBox<String> cbStartHour;
    @FXML
    private ComboBox<String> cbStartMinute;
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
    // Image upload fields
    @FXML
    private ImageView imgPreview;
    @FXML
    private Button btnCreateAuction;
    @FXML
    private Button btnUploadImage;
    @FXML
    private Label lblImageStatus;

    private String selectedImageBase64;
    private File selectedImageFile;
    private boolean editMode;
    private int editingAuctionId = -1;
    private String editingImageUrl;
    private boolean removeImageOnSave;

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
        registerPushListener();
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
        CreateAuctionRequest request = buildCreateAuctionRequest();
        boolean updating = editMode;
        int auctionIdToUpdate = editingAuctionId;
        boolean removeImage = removeImageOnSave;
        Task<Auction> task = new Task<>() {
            @Override
            protected Auction call() {
                if (updating) {
                    return sellerClient.updateAuction(auctionIdToUpdate, request, removeImage);
                }
                return sellerClient.createAuction(request);
            }
        };

        task.setOnRunning(event -> setCreateAuctionInProgress(true));
        task.setOnSucceeded(event -> {
            setCreateAuctionInProgress(false);
            Auction auction = task.getValue();
            String message = updating ? "Đã cập nhật phiên: " : "Đã tạo phiên: ";
            showAlert(Alert.AlertType.INFORMATION, "Thành công", message + auction.getAuctionId());
            clearAuctionForm();
            loadSellerDashboardData();
        });
        task.setOnFailed(event -> {
            setCreateAuctionInProgress(false);
            showAlert(Alert.AlertType.ERROR, "Lỗi", getTaskErrorMessage(task));
        });
        backgroundExecutor.submit(task);
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

        AuctionDetailController.closeAllWindows();
        try {
            User switchedUser = authClient.switchRole(ClientSession.getCurrentUser().getId(), "bidder");
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
            sellerClient.endAuction(currentSellerId, row.id);
            loadSellerDashboardData();
        } catch (RuntimeException e) {
            showAlert(Alert.AlertType.ERROR, "Lỗi", e.getMessage());
        }
    }

    @FXML
    private void handleEditAuction() {
        if (!ensureSellerCanContinue()) {
            return;
        }
        AuctionRow row = tblMyAuctions.getSelectionModel().getSelectedItem();
        if (row == null) {
            showAlert(Alert.AlertType.WARNING, "Cảnh báo", "Vui lòng chọn phiên.");
            return;
        }
        if (!AuctionStatus.OPEN.name().equals(row.status)) {
            showAlert(Alert.AlertType.WARNING, "Cảnh báo", "Chỉ sửa được phiên đang ở trạng thái OPEN.");
            return;
        }

        Task<Auction> task = new Task<>() {
            @Override
            protected Auction call() {
                return bidClient.findAuctionById(Integer.parseInt(row.id))
                        .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy phiên: " + row.id));
            }
        };
        task.setOnSucceeded(event -> {
            Auction auction = task.getValue();
            if (auction.getStatus() != AuctionStatus.OPEN) {
                showAlert(Alert.AlertType.WARNING, "Cảnh báo", "Phiên này không còn ở trạng thái OPEN.");
                loadSellerDashboardData();
                return;
            }
            enterEditMode(auction);
        });
        task.setOnFailed(event -> showAlert(Alert.AlertType.ERROR, "Lỗi", getTaskErrorMessage(task)));
        backgroundExecutor.submit(task);
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

        Auction auction = bidClient.findAuctionById(Integer.parseInt(row.id)).orElse(null);
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
        try {
            authClient.logout();
        } catch (Exception ignored) {
        }
        ClientSession.clear();
        shutdown();
        showLoginScreen();
        showAlert(Alert.AlertType.INFORMATION, "Thông báo", "Đã đăng xuất.");
    }

    private boolean ensureSellerCanContinue() {
        User currentUser = ClientSession.getCurrentUser();
        if (currentUser == null) {
            return redirectToLogin("Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.");
        }

        try {
            User latestUser = authClient.getUserById(currentUser.getId()).orElse(null);
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
            LocalDateTime start = getSelectedStartTime();
            if (!LocalDateTime.now().isBefore(start)) {
                return "Thời gian bắt đầu phải sau hiện tại.";
            }
            LocalDateTime end = getSelectedEndTime();
            if (!start.isBefore(end)) {
                return "Thời gian kết thúc phải sau thời gian bắt đầu.";
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
        clearAuctionFormFields();
        hideTypeSpecificFields();
        resetAuctionFormMode();
    }

    private void clearAuctionFormFields() {
        txtItemName.clear();
        cbItemType.setValue(null);
        txtStartPrice.clear();
        setDefaultStartTime();
        setDefaultEndTime();
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
        clearImageSelection();
    }

    private void resetAuctionFormMode() {
        editMode = false;
        editingAuctionId = -1;
        editingImageUrl = null;
        removeImageOnSave = false;
        cbItemType.setDisable(false);
        if (lblAuctionFormTitle != null) {
            lblAuctionFormTitle.setText("Tạo Phiên Đấu Giá Mới");
        }
        if (tabCreateAuction != null) {
            tabCreateAuction.setText("Tạo Phiên Đấu Giá");
        }
        if (btnCreateAuction != null) {
            btnCreateAuction.setText("Tạo Phiên Đấu Giá");
        }
    }

    @FXML
    private void handleUploadImage() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Chọn ảnh sản phẩm");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Hình ảnh", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp", "*.webp"),
                new FileChooser.ExtensionFilter("Tất cả file", "*.*"));

        File selectedFile = fileChooser.showOpenDialog(btnUploadImage.getScene().getWindow());
        if (selectedFile != null) {
            try {
                byte[] fileBytes = readFileToByteArray(selectedFile);

                if (fileBytes.length > MAX_IMAGE_SIZE_BYTES) {
                    showAlert(Alert.AlertType.WARNING, "Lỗi", "Ảnh phải nhỏ hơn 15MB.");
                    return;
                }

                String mimeType = detectImageMimeType(selectedFile);
                if (mimeType == null) {
                    showAlert(Alert.AlertType.WARNING, "Lỗi", "Chỉ hỗ trợ ảnh JPG, PNG, GIF, BMP hoặc WEBP.");
                    return;
                }

                Image previewImage = new Image(selectedFile.toURI().toString(), 120, 120, true, true, true);
                if (previewImage.isError()) {
                    throw new IOException("File đã chọn không phải ảnh hợp lệ.");
                }

                selectedImageBase64 = buildImageDataUri(fileBytes, mimeType);
                selectedImageFile = selectedFile;
                editingImageUrl = null;
                removeImageOnSave = false;
                imgPreview.setImage(previewImage);
                lblImageStatus
                        .setText("Đã chọn: " + selectedFile.getName() + " (" + formatFileSize(fileBytes.length) + ")");
                lblImageStatus.setStyle("-fx-text-fill: #27ae60;");
            } catch (IOException e) {
                showAlert(Alert.AlertType.ERROR, "Lỗi", "Không thể đọc file ảnh: " + e.getMessage());
            }
        }
    }

    @FXML
    private void handleRemoveImage() {
        boolean shouldRemoveExistingImage = editMode && (hasText(editingImageUrl) || selectedImageBase64 != null);
        clearImageSelection();
        if (shouldRemoveExistingImage) {
            editingImageUrl = null;
            removeImageOnSave = true;
            lblImageStatus.setText("Sẽ xóa ảnh hiện tại khi lưu");
            lblImageStatus.setStyle("-fx-text-fill: #e74c3c;");
        }
    }

    private void clearImageSelection() {
        selectedImageBase64 = null;
        selectedImageFile = null;
        imgPreview.setImage(null);
        lblImageStatus.setText("Chưa chọn ảnh");
        lblImageStatus.setStyle("-fx-text-fill: #7f8c8d;");
    }

    private void enterEditMode(Auction auction) {
        if (auction == null || auction.getItem() == null) {
            showAlert(Alert.AlertType.ERROR, "Lỗi", "Phiên đấu giá không có dữ liệu sản phẩm hợp lệ.");
            return;
        }
        if (!currentSellerId.equals(auction.getSellerId())) {
            showAlert(Alert.AlertType.ERROR, "Lỗi", "Bạn không có quyền sửa phiên này.");
            return;
        }

        clearAuctionFormFields();
        editMode = true;
        editingAuctionId = auction.getAuctionId();
        removeImageOnSave = false;

        Item item = auction.getItem();
        txtItemName.setText(item.getName());
        txtStartPrice.setText(formatEditablePrice(item.getStartingPrice()));
        txtDescription.setText(stripTypeSpecificDescriptionLines(item.getDescription()));
        cbItemType.setValue(toItemTypeDisplay(item));
        handleItemTypeChanged();
        cbItemType.setDisable(true);
        populateTypeSpecificFields(item);
        setDateTimeFields(auction.getStartTime(), auction.getEndTime());
        showExistingImage(item.getImageUrl());

        if (lblAuctionFormTitle != null) {
            lblAuctionFormTitle.setText("Sửa Phiên Đấu Giá #" + auction.getAuctionId());
        }
        if (tabCreateAuction != null) {
            tabCreateAuction.setText("Sửa Phiên Đấu Giá");
        }
        if (btnCreateAuction != null) {
            btnCreateAuction.setText("Lưu thay đổi");
        }
        if (sellerTabPane != null && tabCreateAuction != null) {
            sellerTabPane.getSelectionModel().select(tabCreateAuction);
        }
    }

    private void populateTypeSpecificFields(Item item) {
        String description = item.getDescription();
        if (item instanceof Art art) {
            txtArtist.setText(nullToEmpty(art.getArtist()));
            txtArtYear.setText(extractLineValue(description, "Năm sáng tác"));
            txtMaterial.setText(extractLineValue(description, "Chất liệu"));
        } else if (item instanceof Electronics) {
            txtBrand.setText(extractLineValue(description, "Thương hiệu"));
            txtModel.setText(extractLineValue(description, "Model"));
            txtCondition.setText(extractLineValue(description, "Tình trạng"));
        } else if (item instanceof Vehicle vehicle) {
            txtVehicleBrand.setText(extractLineValue(description, "Hãng xe"));
            txtMileage.setText(String.valueOf(vehicle.getMileage()));
            txtVehicleYear.setText(extractLineValue(description, "Năm sản xuất"));
        }
    }

    private void setDateTimeFields(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime != null) {
            dpStartDate.setValue(startTime.toLocalDate());
            cbStartHour.setValue(String.format("%02d", startTime.getHour()));
            cbStartMinute.setValue(String.format("%02d", startTime.getMinute()));
        }
        if (endTime != null) {
            dpEndDate.setValue(endTime.toLocalDate());
            cbEndHour.setValue(String.format("%02d", endTime.getHour()));
            cbEndMinute.setValue(String.format("%02d", endTime.getMinute()));
        }
    }

    private void showExistingImage(String imageUrl) {
        editingImageUrl = hasText(imageUrl) ? imageUrl.trim() : null;
        selectedImageBase64 = null;
        selectedImageFile = null;
        removeImageOnSave = false;
        if (editingImageUrl == null) {
            clearImageSelection();
            return;
        }
        imgPreview.setImage(new Image(editingImageUrl, 120, 120, true, true, true));
        lblImageStatus.setText("Đang dùng ảnh hiện tại");
        lblImageStatus.setStyle("-fx-text-fill: #27ae60;");
    }

    private String toItemTypeDisplay(Item item) {
        if (item instanceof Art) {
            return "Tác phẩm nghệ thuật";
        }
        if (item instanceof Electronics) {
            return "Điện tử";
        }
        if (item instanceof Vehicle) {
            return "Phương tiện";
        }
        return item.getClass_SimpleName();
    }

    private String stripTypeSpecificDescriptionLines(String description) {
        if (!hasText(description)) {
            return "";
        }
        return description.lines()
                .filter(line -> !isTypeSpecificLine(line))
                .collect(Collectors.joining("\n"))
                .trim();
    }

    private boolean isTypeSpecificLine(String line) {
        String text = line == null ? "" : line.trim();
        return text.startsWith("Hãng xe:")
                || text.startsWith("Năm sản xuất:")
                || text.startsWith("Tình trạng:")
                || text.startsWith("Thương hiệu:")
                || text.startsWith("Model:")
                || text.startsWith("Năm sáng tác:")
                || text.startsWith("Chất liệu:");
    }

    private String extractLineValue(String description, String key) {
        if (!hasText(description)) {
            return "";
        }
        String prefix = key + ":";
        return description.lines()
                .map(String::trim)
                .filter(line -> line.startsWith(prefix))
                .map(line -> line.substring(prefix.length()).trim())
                .findFirst()
                .orElse("");
    }

    private String formatEditablePrice(double price) {
        if (price == Math.rint(price)) {
            return String.valueOf((long) price);
        }
        return String.format(Locale.ROOT, "%.0f", price);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private byte[] readFileToByteArray(File file) throws IOException {
        return Files.readAllBytes(file.toPath());
    }

    private CreateAuctionRequest buildCreateAuctionRequest() {
        return new CreateAuctionRequest(
                currentSellerId,
                txtItemName.getText().trim(),
                cbItemType.getValue(),
                Double.parseDouble(txtStartPrice.getText().trim()),
                txtDescription.getText().trim(),
                getSelectedStartTime(),
                getSelectedEndTime(),
                txtArtist.getText(),
                txtArtYear.getText(),
                txtMaterial.getText(),
                txtBrand.getText(),
                txtModel.getText(),
                txtCondition.getText(),
                txtVehicleBrand.getText(),
                txtMileage.getText(),
                txtVehicleYear.getText(),
                selectedImageBase64);
    }

    private void setCreateAuctionInProgress(boolean inProgress) {
        if (btnCreateAuction != null) {
            btnCreateAuction.setDisable(inProgress);
            if (editMode) {
                btnCreateAuction.setText(inProgress ? "Đang lưu..." : "Lưu thay đổi");
            } else {
                btnCreateAuction.setText(inProgress ? "Đang tạo..." : "Tạo Phiên Đấu Giá");
            }
        }
        if (btnUploadImage != null) {
            btnUploadImage.setDisable(inProgress);
        }
        if (lblImageStatus != null && inProgress && selectedImageFile != null) {
            lblImageStatus.setText("Đang upload: " + selectedImageFile.getName());
            lblImageStatus.setStyle("-fx-text-fill: #1f6feb;");
        } else if (!inProgress && selectedImageFile != null) {
            lblImageStatus.setText(
                    "Đã chọn: " + selectedImageFile.getName() + " (" + formatFileSize(selectedImageFile.length())
                            + ")");
            lblImageStatus.setStyle("-fx-text-fill: #27ae60;");
        }
    }

    private String buildImageDataUri(byte[] fileBytes, String mimeType) {
        return "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(fileBytes);
    }

    private String detectImageMimeType(File file) throws IOException {
        String mimeType = Files.probeContentType(file.toPath());
        if (isSupportedImageMimeType(mimeType)) {
            return mimeType;
        }

        String fileName = file.getName().toLowerCase(Locale.ROOT);
        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (fileName.endsWith(".png")) {
            return "image/png";
        }
        if (fileName.endsWith(".gif")) {
            return "image/gif";
        }
        if (fileName.endsWith(".bmp")) {
            return "image/bmp";
        }
        if (fileName.endsWith(".webp")) {
            return "image/webp";
        }
        return null;
    }

    private boolean isSupportedImageMimeType(String mimeType) {
        return mimeType != null && switch (mimeType.toLowerCase(Locale.ROOT)) {
            case "image/jpeg", "image/png", "image/gif", "image/bmp", "image/webp" -> true;
            default -> false;
        };
    }

    private String formatFileSize(long bytes) {
        if (bytes >= 1024 * 1024) {
            return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
        }
        return String.format(Locale.ROOT, "%.0f KB", Math.max(1, bytes / 1024.0));
    }

    private void configurePriceFields() {
        txtStartPrice.setTextFormatter(
                new TextFormatter<>(change -> change.getControlNewText().matches("\\d*") ? change : null));
    }

    private void configureEndTimeFields() {
        dpStartDate.setConverter(buildDateConverter("bắt đầu"));
        dpEndDate.setConverter(buildDateConverter("kết thúc"));

        populateTimeOptions(cbStartHour, 24);
        populateTimeOptions(cbStartMinute, 60);
        populateTimeOptions(cbEndHour, 24);
        populateTimeOptions(cbEndMinute, 60);
        setDefaultStartTime();
        setDefaultEndTime();
    }

    private StringConverter<LocalDate> buildDateConverter(String fieldName) {
        return new StringConverter<>() {
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
                    throw new IllegalArgumentException("Ngày " + fieldName + " phải có dạng dd/MM/yyyy.");
                }
            }
        };
    }

    private void populateTimeOptions(ComboBox<String> comboBox, int maxExclusive) {
        if (!comboBox.getItems().isEmpty()) {
            return;
        }
        for (int value = 0; value < maxExclusive; value++) {
            comboBox.getItems().add(String.format("%02d", value));
        }
    }

    private void setDefaultStartTime() {
        LocalDateTime defaultStart = LocalDateTime.now().plusMinutes(5);
        dpStartDate.setValue(defaultStart.toLocalDate());
        cbStartHour.setValue(String.format("%02d", defaultStart.getHour()));
        cbStartMinute.setValue(String.format("%02d", defaultStart.getMinute()));
    }

    private void setDefaultEndTime() {
        dpEndDate.setValue(LocalDate.now().plusDays(1));
        cbEndHour.setValue("23");
        cbEndMinute.setValue("59");
    }

    private LocalDateTime getSelectedStartTime() {
        LocalDate date = dpStartDate.getValue();
        String hour = cbStartHour.getValue();
        String minute = cbStartMinute.getValue();
        if (date == null || hour == null || minute == null) {
            throw new IllegalArgumentException("Vui lòng chọn đầy đủ ngày, giờ và phút bắt đầu.");
        }
        return date.atTime(Integer.parseInt(hour), Integer.parseInt(minute));
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
                List<Auction> sellerAuctions = sellerClient.getSellerAuctions(currentSellerId);
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
        double currentBid = auction.getCurrentHighestBid();
        double startingPrice = auction.getItem() != null ? auction.getItem().getStartingPrice() : 0;
        double displayCurrentPrice = currentBid > 0 ? currentBid : startingPrice;
        return new AuctionRow(
                String.valueOf(auction.getAuctionId()),
                auction.getItem() == null ? "-" : auction.getItem().getName(),
                auction.getItem() == null ? "-" : formatCurrency(startingPrice),
                formatCurrency(displayCurrentPrice),
                auction.getStatus().name(),
                FormatUtils.formatDateTime(auction.getEndTime()));
    }

    private Comparator<Auction> resolveAuctionComparator() {
        return resolveAuctionComparator(cbSortBy.getValue());
    }

    private Comparator<Auction> resolveAuctionComparator(String sortBy) {
        if (sortBy == null) {
            sortBy = "Mới nhất";
        }
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

    private void registerPushListener() {
        ClientSession.getSocket().setPushListener((event, data) -> {
            Platform.runLater(() -> {
                try {
                    loadSellerDashboardData();
                } catch (RuntimeException ignored) {
                }
            });
        });
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
