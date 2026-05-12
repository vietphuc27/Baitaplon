package client.controller;

import client.application.ClientSession;
import common.models.user.User;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import server.service.UserService;

import java.io.IOException;

public class LoginController {
    private final UserService userService = new UserService();

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Button loginBtn;
    @FXML private Label errorLabel;

    @FXML private TextField signUpUsernameField;
    @FXML private TextField emailField;
    @FXML private ComboBox<String> roleComboBox;
    @FXML private PasswordField signUpPasswordField;
    @FXML private Button registerBtn;
    @FXML private Label signUpErrorLabel;

    @FXML
    private void initialize() {
        roleComboBox.setItems(FXCollections.observableArrayList("Seller", "Bidder"));

        loginBtn.setOnAction(this::handleLogin);
        registerBtn.setOnAction(this::handleRegister);
        loginBtn.setDefaultButton(true);

        usernameField.setOnAction(this::handleLogin);
        passwordField.setOnAction(this::handleLogin);
        signUpUsernameField.setOnAction(this::handleRegister);
        emailField.setOnAction(this::handleRegister);
        roleComboBox.setOnAction(event -> registerBtn.setDefaultButton(true));
        signUpPasswordField.setOnAction(this::handleRegister);

        clearError(errorLabel);
        clearError(signUpErrorLabel);
    }

    private void handleLogin(ActionEvent event) {
        String username = usernameField.getText() == null ? "" : usernameField.getText().trim();
        String password = passwordField.getText() == null ? "" : passwordField.getText().trim();

        if (username.isEmpty() || password.isEmpty()) {
            showError(errorLabel, "Nhập username và password.");
            return;
        }

        try {
            User user = userService.login(username, password);
            ClientSession.setCurrentUser(user);
            clearError(errorLabel);
            openDashboard(event, user.getRole());
        } catch (IOException e) {
            showError(errorLabel, "Không mở được dashboard: " + e.getMessage());
        } catch (RuntimeException e) {
            showError(errorLabel, e.getMessage());
        }
    }

    private void handleRegister(ActionEvent event) {
        String username = signUpUsernameField.getText() == null ? "" : signUpUsernameField.getText().trim();
        String email = emailField.getText() == null ? "" : emailField.getText().trim();
        String password = signUpPasswordField.getText() == null ? "" : signUpPasswordField.getText().trim();
        String role = roleComboBox.getValue();

        if (username.isEmpty() || email.isEmpty() || password.isEmpty() || role == null) {
            showError(signUpErrorLabel, "Điền đủ thông tin đăng ký.");
            return;
        }

        try {
            User createdUser = userService.register(username, email, password, role);
            signUpErrorLabel.setText("Đăng ký thành công. UserID: " + createdUser.getId());
            signUpErrorLabel.setVisible(true);
            signUpUsernameField.clear();
            signUpPasswordField.clear();
            emailField.clear();
            roleComboBox.setValue(null);
        } catch (RuntimeException e) {
            showError(signUpErrorLabel, e.getMessage());
        }
    }

    private void openDashboard(ActionEvent event, String role) throws IOException {
        String normalizedRole = role == null ? "" : role.trim().toUpperCase();
        String viewPath;

        switch (normalizedRole) {
            case "SELLER":
                viewPath = "/view/SellerDashboardView.fxml";
                break;
            case "BIDDER":
                viewPath = "/view/BidderDashboardView.fxml";
                break;
            case "ADMIN":
                viewPath = "/view/AdminDashboardView.fxml";
                break;
            default:
                throw new IllegalArgumentException("Role không được hỗ trợ: " + role);
        }

        Parent root = FXMLLoader.load(getClass().getResource(viewPath));
        Stage stage = (Stage) ((Control) event.getSource()).getScene().getWindow();
        stage.setScene(new Scene(root));
        stage.show();
    }

    private void showError(Label label, String message) {
        label.setText(message);
        label.setVisible(true);
    }

    private void clearError(Label label) {
        label.setText("");
        label.setVisible(false);
    }
}
