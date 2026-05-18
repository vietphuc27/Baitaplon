package client.controller;

import client.application.ClientSession;
import client.network.AdminClient;
import client.network.BidClient;
import common.models.auction.Auction;
import common.models.auction.AuctionStatus;
import common.models.user.User;
import common.utils.FormatUtils;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class AdminController {
    @FXML private TextField txtSearch;
    @FXML private ComboBox<String> cbFilterStatus;
    @FXML private ComboBox<String> cbSortBy;
    @FXML private TextField txtAuctionSearch;
    @FXML private ComboBox<String> cbAuctionFilterStatus;
    @FXML private ComboBox<String> cbAuctionSortBy;
    @FXML private TableView<UserRow> userTable;
    @FXML private TableColumn<UserRow, String> userIdCol;
    @FXML private TableColumn<UserRow, String> usernameCol;
    @FXML private TableColumn<UserRow, String> emailCol;
    @FXML private TableColumn<UserRow, String> roleCol;
    @FXML private TableColumn<UserRow, String> statusCol;
    @FXML private TableView<AuctionRow> auctionTable;
    @FXML private TableColumn<AuctionRow, String> auctionIdCol;
    @FXML private TableColumn<AuctionRow, String> itemNameCol;
    @FXML private TableColumn<AuctionRow, String> currentBidCol;
    @FXML private TableColumn<AuctionRow, String> auctionStatusCol;

    private final AdminClient adminClient = new AdminClient();
    private final BidClient bidClient = new BidClient();
    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();

    @FXML
    public void initialize() {
        cbFilterStatus.getItems().addAll("ALL", "LOGIN", "LOGOUT", "BANNED");
        cbFilterStatus.setValue("ALL");
        cbSortBy.getItems().addAll("Username", "Role", "Status");
        cbSortBy.setValue("Username");
        cbAuctionFilterStatus.getItems().addAll("ALL", "OPEN", "RUNNING", "FINISHED","PAID" , "CANCELED");
        cbAuctionFilterStatus.setValue("ALL");
        cbAuctionSortBy.getItems().addAll("ID", "Item name", "Current bid", "Status");
        cbAuctionSortBy.setValue("ID");

        userIdCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().id));
        usernameCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().username));
        emailCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().email));
        roleCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().role));
        statusCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().status));
        auctionIdCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().id));
        itemNameCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().itemName));
        currentBidCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().currentBid));
        auctionStatusCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().status));
        Platform.runLater(() -> {
            if (txtSearch.getScene() != null && txtSearch.getScene().getWindow() != null) {
                txtSearch.getScene().getWindow().addEventHandler(WindowEvent.WINDOW_HIDDEN, event -> shutdown());
            }
        });
        refreshData();
    }

    @FXML
    public void banUser() {
        UserRow selectedUser = userTable.getSelectionModel().getSelectedItem();
        if (selectedUser == null) return;
        adminClient.banUser(Integer.parseInt(selectedUser.id));
        refreshData();
    }

    @FXML
    public void activateUser() {
        UserRow selectedUser = userTable.getSelectionModel().getSelectedItem();
        if (selectedUser == null) return;
        adminClient.unbanUser(Integer.parseInt(selectedUser.id));
        refreshData();
    }

    @FXML
    public void cancelAuction() {
        AuctionRow selectedAuction = auctionTable.getSelectionModel().getSelectedItem();
        if (selectedAuction == null) {
            showAlert(Alert.AlertType.WARNING, "Cảnh báo", "Vui lòng chọn phiên đấu giá cần hủy.");
            return;
        }
        try {
            adminClient.cancelAuction(Integer.parseInt(selectedAuction.id));
            refreshData();
        } catch (RuntimeException e) {
            showAlert(Alert.AlertType.ERROR, "Lỗi", e.getMessage());
        }
    }

    @FXML
    public void viewAuctionDetails() {
        AuctionRow selectedAuction = auctionTable.getSelectionModel().getSelectedItem();
        if (selectedAuction == null) {
            showAlert(Alert.AlertType.WARNING, "Cảnh báo", "Vui lòng chọn phiên đấu giá.");
            return;
        }

        Auction auction = bidClient.findAuctionById(Integer.parseInt(selectedAuction.id)).orElse(null);
        if (auction == null) {
            showAlert(Alert.AlertType.ERROR, "Lỗi", "Không tìm thấy phiên: " + selectedAuction.id);
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
            stage.show();
        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Lỗi", "Không mở được màn hình chi tiết: " + e.getMessage());
        }
    }

    @FXML
    public void searchUsers() {
        loadUsers();
    }

    @FXML
    public void searchAuctions() {
        loadAllAuctions();
    }

    @FXML
    public void loadUsers() {
        String keyword = txtSearch.getText() == null ? "" : txtSearch.getText().trim().toLowerCase(Locale.ROOT);
        String status = cbFilterStatus.getValue();
        String sortBy = cbSortBy.getValue();

        Task<List<UserRow>> task = new Task<>() {
            @Override
            protected List<UserRow> call() {
                return adminClient.getAllUsers().stream()
                        .filter(u -> {
                            String username = u.getUsername() == null ? "" : u.getUsername().toLowerCase(Locale.ROOT);
                            String email = u.getEmail() == null ? "" : u.getEmail().toLowerCase(Locale.ROOT);
                            return keyword.isEmpty() || username.contains(keyword) || email.contains(keyword);
                        })
                        .filter(u -> "ALL".equals(status) || u.getStatus().name().equalsIgnoreCase(status))
                        .map(u -> new UserRow(String.valueOf(u.getId()), u.getUsername(), u.getEmail(), u.getRole(), u.getStatus().name()))
                        .sorted(resolveUserComparator(sortBy))
                        .collect(Collectors.toList());
            }
        };

        task.setOnSucceeded(event -> userTable.getItems().setAll(task.getValue()));
        task.setOnFailed(event -> showAlert(Alert.AlertType.ERROR, "Lỗi", getTaskErrorMessage(task)));
        backgroundExecutor.submit(task);
    }

    @FXML
    public void loadAllAuctions() {
        String keyword = txtAuctionSearch.getText() == null
                ? ""
                : txtAuctionSearch.getText().trim().toLowerCase(Locale.ROOT);
        String status = cbAuctionFilterStatus.getValue();
        String sortBy = cbAuctionSortBy.getValue();

        Task<List<AuctionRow>> task = new Task<>() {
            @Override
            protected List<AuctionRow> call() {
                return bidClient.getAllAuctions().stream()
                        .filter(a -> {
                            String id = String.valueOf(a.getAuctionId());
                            String itemName = a.getItem() == null ? "" : a.getItem().getName();
                            return keyword.isEmpty()
                                    || id.contains(keyword)
                                    || itemName.toLowerCase(Locale.ROOT).contains(keyword);
                        })
                        .filter(a -> "ALL".equals(status) || a.getStatus().name().equalsIgnoreCase(status))
                        .map(a -> new AuctionRow(
                                String.valueOf(a.getAuctionId()),
                                a.getItem() == null ? "-" : a.getItem().getName(),
                                FormatUtils.formatCurrency(a.getCurrentHighestBid()),
                                a.getStatus().name()
                        ))
                        .sorted(resolveAuctionComparator(sortBy))
                        .collect(Collectors.toList());
            }
        };

        task.setOnSucceeded(event -> auctionTable.getItems().setAll(task.getValue()));
        task.setOnFailed(event -> showAlert(Alert.AlertType.ERROR, "Lỗi", getTaskErrorMessage(task)));
        backgroundExecutor.submit(task);
    }

    @FXML
    public void refreshData() {
        loadUsers();
        loadAllAuctions();
    }

    @FXML
    public void logout() throws IOException {
        ClientSession.clear();
        shutdown();
        Parent root = FXMLLoader.load(getClass().getResource("/view/LogInView.fxml"));
        Stage stage = (Stage) txtSearch.getScene().getWindow();
        stage.setScene(new Scene(root));
        stage.show();
        showAlert(Alert.AlertType.INFORMATION, "Thông báo", "Đã đăng xuất.");
    }

    private Comparator<UserRow> resolveUserComparator(String sortBy) {
        return switch (sortBy) {
            case "Role" -> Comparator.comparing(r -> r.role);
            case "Status" -> Comparator.comparing(r -> r.status);
            default -> Comparator.comparing(r -> r.username);
        };
    }

    private Comparator<AuctionRow> resolveAuctionComparator(String sortBy) {
        return switch (sortBy) {
            case "Item name" -> Comparator.comparing(r -> r.itemName);
            case "Current bid" -> Comparator.comparingDouble(r -> parseAmount(r.currentBid));
            case "Status" -> Comparator.comparing(r -> r.status);
            default -> Comparator.comparingInt(r -> Integer.parseInt(r.id));
        };
    }

    private double parseAmount(String value) {
        try {
            return Double.parseDouble(value.replaceAll("[^0-9.-]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String getTaskErrorMessage(Task<?> task) {
        Throwable exception = task.getException();
        return exception == null ? "Không tải được dữ liệu." : exception.getMessage();
    }

    private void shutdown() {
        backgroundExecutor.shutdownNow();
    }

    public static class UserRow {
        private final String id;
        private final String username;
        private final String email;
        private final String role;
        private final String status;
        public UserRow(String id, String username, String email, String role, String status) {
            this.id = id; this.username = username; this.email = email; this.role = role; this.status = status;
        }
    }

    public static class AuctionRow {
        private final String id;
        private final String itemName;
        private final String currentBid;
        private final String status;
        public AuctionRow(String id, String itemName, String currentBid, String status) {
            this.id = id; this.itemName = itemName; this.currentBid = currentBid; this.status = status;
        }
    }
    private void showAlert(Alert.AlertType type, String title, String msg) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }
}