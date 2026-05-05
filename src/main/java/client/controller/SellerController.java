package client.controller;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ResourceBundle;

public class SellerController implements Initializable {
    
    // Header Controls
    @FXML
    private Label lblSellerName;
    
    // Tab 1: Create Auction
    @FXML
    private TextField txtItemName;
    @FXML
    private ComboBox<String> cbItemType;
    @FXML
    private TextField txtStartPrice;
    @FXML
    private TextField txtBuyNowPrice;
    @FXML
    private TextField txtEndTime;
    @FXML
    private TextArea txtDescription;
    
    // Dynamic Fields Container
    @FXML
    private VBox vboxDynamicFields;
    @FXML
    private Label lblDynamicTitle;
    
    // Art Fields
    @FXML
    private GridPane gridArt;
    @FXML
    private TextField txtArtist;
    @FXML
    private TextField txtArtYear;
    @FXML
    private TextField txtMaterial;
    
    // Electronics Fields
    @FXML
    private GridPane gridElectronics;
    @FXML
    private TextField txtBrand;
    @FXML
    private TextField txtModel;
    @FXML
    private TextField txtCondition;
    
    // Vehicle Fields
    @FXML
    private GridPane gridVehicle;
    @FXML
    private TextField txtVehicleBrand;
    @FXML
    private TextField txtMileage;
    @FXML
    private TextField txtVehicleYear;
    
    // Tab 2: My Auctions
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
    
    // Tab 3: Statistics
    @FXML
    private Label lblTotalAuctions;
    @FXML
    private Label lblActiveAuctions;
    @FXML
    private Label lblTotalRevenue;
    private String labubu; 
    
    private String currentSellerName;
    private DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        initializeSellerName();
        setupItemTypeComboBox();
        setupSortByComboBox();
        setupTableColumns();
        setupEventListeners();
        loadSellerAuctions();
        loadStatistics();
    }
    
    private void initializeSellerName() {
        // Get seller name from ClientSession (implementation depends on your session management)
        // jajajaja
        currentSellerName = "Người Bán 01"; // Placeholder
        lblSellerName.setText("Seller: " + currentSellerName);
    }
    
    private void setupItemTypeComboBox() {
        cbItemType.getItems().addAll("Tác phẩm nghệ thuật", "Điện tử", "Phương tiện");
        cbItemType.setOnAction(event -> handleItemTypeChanged());
    }
    
    private void setupSortByComboBox() {
        cbSortBy.getItems().addAll("Mới nhất", "Cũ nhất", "Trạng thái", "Giá tăng", "Giá giảm");
        cbSortBy.setValue("Mới nhất");
    }
    
    private void setupTableColumns() {
        colAuctionId.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().getId()));
        colAuctionItem.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().getItemName()));
        colStartPrice.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().getStartPrice()));
        colCurrentPrice.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().getCurrentPrice()));
        colStatus.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().getStatus()));
        colEndTime.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().getEndTime()));
    }
    
    private void setupEventListeners() {
        // Add button event handlers here when UI events are bound
    }
    
    @FXML
    private void handleCreateAuction() {
        if (validateAuctionForm()) {
            // Collect form data
            String itemName = txtItemName.getText();
            String itemType = cbItemType.getValue();
            double startPrice = Double.parseDouble(txtStartPrice.getText());
            String description = txtDescription.getText();
            LocalDateTime endTime = parseEndTime(txtEndTime.getText());
            
            // Create auction object and send to server
            // TODO: Implement auction creation via AuctionClient
            
            showAlert(Alert.AlertType.INFORMATION, "Thành công", "Phiên đấu giá đã được tạo!");
            clearAuctionForm();
            loadSellerAuctions();
            loadStatistics();
        }
    }
    
    @FXML
    private void handleClearForm() {
        clearAuctionForm();
        hideTypeSpecificFields();
    }
    
    @FXML
    private void handleRefreshData() {
        loadSellerAuctions();
        loadStatistics();
        showAlert(Alert.AlertType.INFORMATION, "Thông báo", "Dữ liệu đã được làm mới!");
    }
    
    @FXML
    private void handleEndAuction() {
        AuctionRow selectedAuction = tblMyAuctions.getSelectionModel().getSelectedItem();
        if (selectedAuction == null) {
            showAlert(Alert.AlertType.WARNING, "Cảnh báo", "Vui lòng chọn một phiên đấu giá!");
            return;
        }
        
        // TODO: Send end auction request to server
        showAlert(Alert.AlertType.INFORMATION, "Thành công", "Phiên đấu giá đã được kết thúc!");
        loadSellerAuctions();
        loadStatistics();
    }
    
    @FXML
    private void handleViewAuctionDetails() {
        AuctionRow selectedAuction = tblMyAuctions.getSelectionModel().getSelectedItem();
        if (selectedAuction == null) {
            showAlert(Alert.AlertType.WARNING, "Cảnh báo", "Vui lòng chọn một phiên đấu giá!");
            return;
        }
        
        // TODO: Open auction details view
        showAlert(Alert.AlertType.INFORMATION, "Thông tin", "Chi tiết phiên: " + selectedAuction.getId());
    }
    
    @FXML
    private void handleLogout() {
        // TODO: Implement logout logic
        showAlert(Alert.AlertType.INFORMATION, "Thông báo", "Đã đăng xuất!");
    }
    
    private void handleItemTypeChanged() {
        String selectedType = cbItemType.getValue();
        if (selectedType == null) {
            hideTypeSpecificFields();
            return;
        }
        
        vboxDynamicFields.setVisible(true);
        vboxDynamicFields.setManaged(true);
        hideTypeSpecificFields();
        
        switch (selectedType) {
            case "Tác phẩm nghệ thuật":
                lblDynamicTitle.setText("Thông tin tác phẩm nghệ thuật");
                gridArt.setVisible(true);
                gridArt.setManaged(true);
                break;
            case "Điện tử":
                lblDynamicTitle.setText("Thông tin sản phẩm điện tử");
                gridElectronics.setVisible(true);
                gridElectronics.setManaged(true);
                break;
            case "Phương tiện":
                lblDynamicTitle.setText("Thông tin phương tiện");
                gridVehicle.setVisible(true);
                gridVehicle.setManaged(true);
                break;
        }
    }
    
    private void hideTypeSpecificFields() {
        gridArt.setVisible(false);
        gridArt.setManaged(false);
        gridElectronics.setVisible(false);
        gridElectronics.setManaged(false);
        gridVehicle.setVisible(false);
        gridVehicle.setManaged(false);
    }
    
    private boolean validateAuctionForm() {
        if (txtItemName.getText().trim().isEmpty()) {
            showAlert(Alert.AlertType.ERROR, "Lỗi", "Vui lòng nhập tên sản phẩm!");
            return false;
        }
        
        if (cbItemType.getValue() == null) {
            showAlert(Alert.AlertType.ERROR, "Lỗi", "Vui lòng chọn loại sản phẩm!");
            return false;
        }
        
        try {
            Double.parseDouble(txtStartPrice.getText());
        } catch (NumberFormatException e) {
            showAlert(Alert.AlertType.ERROR, "Lỗi", "Giá khởi điểm phải là số!");
            return false;
        }
        
        if (txtEndTime.getText().trim().isEmpty()) {
            showAlert(Alert.AlertType.ERROR, "Lỗi", "Vui lòng nhập thời gian kết thúc!");
            return false;
        }
        
        if (parseEndTime(txtEndTime.getText()) == null) {
            showAlert(Alert.AlertType.ERROR, "Lỗi", "Thời gian không hợp lệ! Định dạng: dd/MM/yyyy HH:mm");
            return false;
        }
        
        return true;
    }
    
    private LocalDateTime parseEndTime(String timeString) {
        try {
            return LocalDateTime.parse(timeString, dateTimeFormatter);
        } catch (Exception e) {
            return null;
        }
    }
    
    private void clearAuctionForm() {
        txtItemName.clear();
        cbItemType.setValue(null);
        txtStartPrice.clear();
        txtBuyNowPrice.clear();
        txtEndTime.clear();
        txtDescription.clear();
        
        // Clear type-specific fields
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
    
    private void loadSellerAuctions() {
        // TODO: Fetch auctions from server and populate table
        // For now, add sample data
        tblMyAuctions.getItems().clear();
        tblMyAuctions.getItems().addAll(
            new AuctionRow("1", "iPhone 15", "15.000.000 VNĐ", "18.500.000 VNĐ", "Đang chạy", "25/04/2026 10:00"),
            new AuctionRow("2", "Tranh sơn dầu", "5.000.000 VNĐ", "6.200.000 VNĐ", "Đang chạy", "26/04/2026 15:30"),
            new AuctionRow("3", "Toyota Camry 2020", "500.000.000 VNĐ", "550.000.000 VNĐ", "Đã kết thúc", "20/04/2026 18:00")
        );
    }
    
    private void loadStatistics() {
        // TODO: Fetch statistics from server
        lblTotalAuctions.setText("12");
        lblActiveAuctions.setText("3");
        lblTotalRevenue.setText("15.700.000 VNĐ");
    }
    
    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    // Inner class for table row data
    public static class AuctionRow {
        private String id;
        private String itemName;
        private String startPrice;
        private String currentPrice;
        private String status;
        private String endTime;
        
        public AuctionRow(String id, String itemName, String startPrice, String currentPrice, String status, String endTime) {
            this.id = id;
            this.itemName = itemName;
            this.startPrice = startPrice;
            this.currentPrice = currentPrice;
            this.status = status;
            this.endTime = endTime;
        }
        
        public String getId() { return id; }
        public String getItemName() { return itemName; }
        public String getStartPrice() { return startPrice; }
        public String getCurrentPrice() { return currentPrice; }
        public String getStatus() { return status; }
        public String getEndTime() { return endTime; }
    }
}
