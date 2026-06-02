package client.controller;

import client.application.ClientSession;
import client.application.DashboardNavigator;
import client.network.AuthClient;
import common.models.user.User;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import java.io.IOException;

public class LoginController {
    private final AuthClient authClient = new AuthClient();

    @FXML
    private TextField usernameField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private TextField passwordVisible;
    @FXML
    private Button togglePasswordBtn;
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
    private TextField signUpPasswordVisible;
    @FXML
    private Button toggleSignUpPasswordBtn;
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

    private boolean passwordShown = false;
    private boolean signUpPasswordShown = false;

    @FXML
    private void initialize() {
        roleComboBox.setItems(FXCollections.observableArrayList("Seller", "Bidder"));

        loginBtn.setOnAction(this::handleLogin);
        registerBtn.setOnAction(this::handleRegister);
        loginBtn.setDefaultButton(true);

        usernameField.setOnAction(this::handleLogin);
        passwordField.setOnAction(this::handleLogin);
        passwordVisible.setOnAction(this::handleLogin);
        signUpUsernameField.setOnAction(this::handleRegister);
        emailField.setOnAction(this::handleRegister);
        roleComboBox.setOnAction(event -> registerBtn.setDefaultButton(true));
        signUpPasswordField.setOnAction(this::handleRegister);
        signUpPasswordVisible.setOnAction(this::handleRegister);

        clearError(errorLabel);
        clearError(signUpErrorLabel);
    }

    @FXML
    private void togglePassword() {
        passwordShown = !passwordShown;
        if (passwordShown) {
            passwordVisible.setText(passwordField.getText());
            passwordVisible.setVisible(true);
            passwordVisible.setManaged(true);
            passwordField.setVisible(false);
            passwordField.setManaged(false);
            togglePasswordBtn.setText("🙈");
            passwordVisible.requestFocus();
        } else {
            passwordField.setText(passwordVisible.getText());
            passwordField.setVisible(true);
            passwordField.setManaged(true);
            passwordVisible.setVisible(false);
            passwordVisible.setManaged(false);
            togglePasswordBtn.setText("👁");
            passwordField.requestFocus();
        }
    }

    @FXML
    private void toggleSignUpPassword() {
        signUpPasswordShown = !signUpPasswordShown;
        if (signUpPasswordShown) {
            signUpPasswordVisible.setText(signUpPasswordField.getText());
            signUpPasswordVisible.setVisible(true);
            signUpPasswordVisible.setManaged(true);
            signUpPasswordField.setVisible(false);
            signUpPasswordField.setManaged(false);
            toggleSignUpPasswordBtn.setText("🙈");
            signUpPasswordVisible.requestFocus();
        } else {
            signUpPasswordField.setText(signUpPasswordVisible.getText());
            signUpPasswordField.setVisible(true);
            signUpPasswordField.setManaged(true);
            signUpPasswordVisible.setVisible(false);
            signUpPasswordVisible.setManaged(false);
            toggleSignUpPasswordBtn.setText("👁");
            signUpPasswordField.requestFocus();
        }
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
        String password = passwordShown
                ? (passwordVisible.getText() == null ? "" : passwordVisible.getText().trim())
                : (passwordField.getText() == null ? "" : passwordField.getText().trim());

        if (username.isEmpty() || password.isEmpty()) {
            showError(errorLabel, "Nhập username và password.");
            return;
        }

        try {
            User user = authClient.login(username, password);
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
        String password = signUpPasswordShown
                ? (signUpPasswordVisible.getText() == null ? "" : signUpPasswordVisible.getText().trim())
                : (signUpPasswordField.getText() == null ? "" : signUpPasswordField.getText().trim());
        String role = roleComboBox.getValue();

        if (username.isEmpty() || email.isEmpty() || password.isEmpty() || role == null) {
            showError(signUpErrorLabel, "Điền đủ thông tin đăng ký.");
            return;
        }

        try {
            User createdUser = authClient.register(username, email, password, role);
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
