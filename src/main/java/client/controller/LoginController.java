package client.controller;

import client.application.ClientSession;
import client.application.DashboardNavigator;
import common.models.user.User;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import server.service.UserService;

import java.io.IOException;

public class LoginController {
    private final UserService userService = new UserService();

    @FXML
    private TextField usernameField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private Button loginBtn;
    @FXML
    private Label errorLabel;

    @FXML
    private TextField signUpUsernameField;
    @FXML
    private TextField emailField;
    @FXML
    private ComboBox<String> roleComboBox;
    @FXML
    private PasswordField signUpPasswordField;
    @FXML
    private Button registerBtn;
    @FXML
    private Label signUpErrorLabel;
    @FXML
    private TabPane authTabPane;
    @FXML
    private Tab signInTab;
    @FXML
    private Tab signUpTab;

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

    @FXML
    private void goToSignUp(ActionEvent event) {
        clearError(errorLabel);
        clearError(signUpErrorLabel);
        authTabPane.getSelectionModel().select(signUpTab);
        registerBtn.setDefaultButton(true);
        loginBtn.setDefaultButton(false);
    }

    @FXML
    private void goToSignIn(ActionEvent event) {
        clearError(errorLabel);
        clearError(signUpErrorLabel);
        authTabPane.getSelectionModel().select(signInTab);
        loginBtn.setDefaultButton(true);
        registerBtn.setDefaultButton(false);
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
            openDashboard(event, user);
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

    private void openDashboard(ActionEvent event, User user) throws IOException {
        Stage stage = (Stage) ((Control) event.getSource()).getScene().getWindow();
        String normalizedRole = user.getRole() == null ? "" : user.getRole().trim().toUpperCase();

        switch (normalizedRole) {
            case "SELLER" -> DashboardNavigator.showSellerDashboard(stage);
            case "BIDDER" -> DashboardNavigator.showBidderDashboard(stage);
            case "ADMIN" -> DashboardNavigator.showAdminDashboard(stage);
            default -> throw new IllegalArgumentException("Role không được hỗ trợ: " + normalizedRole);
        }
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
