package client.controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public class AdminController {
    
    // --- FXML UI components ---
    @FXML private TextField txtSearch;
    @FXML private ComboBox<String> cbFilterStatus;
    @FXML private ComboBox<String> cbSortBy;

    @FXML private TableView<UserRow> userTable;
    @FXML private TableColumn<UserRow, String> userIdCol;
    @FXML private TableColumn<UserRow, String> usernameCol;
    @FXML private TableColumn<UserRow, String> roleCol;
    @FXML private TableColumn<UserRow, String> statusCol;

    @FXML private Button btnBanUser;
    @FXML private Button btnActivateUser;

    @FXML private TableView<AuctionRow> auctionTable;
    @FXML private TableColumn<AuctionRow, String> auctionIdCol;
    @FXML private TableColumn<AuctionRow, String> itemNameCol;
    @FXML private TableColumn<AuctionRow, String> currentBidCol;
    @FXML private TableColumn<AuctionRow, String> auctionStatusCol;

    @FXML private Button btnCancelAuction;
    @FXML private Button btnRefresh;
    @FXML private Button btnLogout;

    // Khởi tạo controller, thiết lập dữ liệu ban đầu cho view
    @FXML
    public void initialize() {
        // Cấu hình các cột cho bảng User
        userIdCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getId()));
        usernameCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getUsername()));
        roleCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getRole()));
        statusCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getStatus()));

        // Cấu hình các cột cho bảng Auction
        auctionIdCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getId()));
        itemNameCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getItemName()));
        currentBidCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getCurrentBid()));
        auctionStatusCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getStatus()));

        // Đổ dữ liệu mẫu ban đầu
        refreshData();
       
    }

    // Khóa tài khoản user theo id
    @FXML
    public void banUser() {
        // Lấy hàng đang được chọn trong bảng User
        UserRow selectedUser = userTable.getSelectionModel().getSelectedItem();
        if (selectedUser != null) {
            System.out.println("Đang thực hiện khóa người dùng: " + selectedUser.getUsername());
            // Sau này: Gửi ID "selectedUser.getId()" qua Socket tới Server
            selectedUser.setStatus("Bị khóa");
            userTable.refresh(); // Cập nhật lại hiển thị trên bảng
        } else {
            System.out.println("Vui lòng chọn một người dùng để khóa!");
        }
    }
        

    // Hủy phiên đấu giá theo id
    @FXML
    public void cancelAuction() {
        AuctionRow selectedAuction = auctionTable.getSelectionModel().getSelectedItem();
        if (selectedAuction != null) {
            System.out.println("Đang hủy phiên đấu giá ID: " + selectedAuction.getId());
            // Sau này: Gửi lệnh hủy qua Socket
            selectedAuction.setStatus("Đã hủy");
            auctionTable.refresh();
        } else {
            System.out.println("Vui lòng chọn một phiên đấu giá để hủy!");
        }
       
    }

    // Lấy danh sách user và hiển thị lên view
    @FXML
    public void loadUsers() {
       
    }

    // Lấy danh sách tất cả các phiên đấu giá và hiển thị lên view
    @FXML
    public void loadAllAuctions() {
       
    }

    // Lấy và hiển thị các thống kê
    @FXML
    public void viewStats() {
        // TODO: Hiển thị thống kê lên biểu đồ
    }

    // Làm mới toàn bộ dữ liệu trên dashboard
    @FXML
    public void refreshData() {
        loadUsers();
        loadAllAuctions();
    }

    // --- Dữ liệu mẫu cho bảng ---
    public static class UserRow {
        private String id;
        private String username;
        private String role;
        private String status;

        public UserRow(String id, String username, String role, String status) {
            this.id = id;
            this.username = username;
            this.role = role;
            this.status = status;
        }
        public String getId() { return id; }
        public String getUsername() { return username; }
        public String getRole() { return role; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    public static class AuctionRow {
        private String id;
        private String itemName;
        private String currentBid;
        private String status;

        public AuctionRow(String id, String itemName, String currentBid, String status) {
            this.id = id;
            this.itemName = itemName;
            this.currentBid = currentBid;
            this.status = status;
        }
        public String getId() { return id; }
        public String getItemName() { return itemName; }
        public String getCurrentBid() { return currentBid; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }
}



