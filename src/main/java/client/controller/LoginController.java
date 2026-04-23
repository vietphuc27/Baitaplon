package client.controller;

import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;

public class LoginController {
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Button loginBtn;
    @FXML private Label errorLabel;

    @FXML private TextField idField;
    @FXML private TextField signUpUsernameField;
    @FXML private TextField emailField;
    @FXML private ComboBox<String> roleComboBox;
    @FXML private PasswordField signUpPasswordField;
    @FXML private Button registerBtn;
    @FXML private Label signUpErrorLabel;

    @FXML
    private void initialize() {
        roleComboBox.setItems(FXCollections.observableArrayList("Seller", "Bidder", "Admin"));

        loginBtn.setOnAction(this::handleLogin);
        registerBtn.setOnAction(this::handleRegister);

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

        // nối UserService.login()
        showError(errorLabel, "Chưa nối UserService.");
    }

    private void handleRegister(ActionEvent event) {
        String id = idField.getText() == null ? "" : idField.getText().trim();
        String username = signUpUsernameField.getText() == null ? "" : signUpUsernameField.getText().trim();
        String email = emailField.getText() == null ? "" : emailField.getText().trim();
        String password = signUpPasswordField.getText() == null ? "" : signUpPasswordField.getText().trim();
        String role = roleComboBox.getValue();

        if (id.isEmpty() || username.isEmpty() || email.isEmpty() || password.isEmpty() || role == null) {
            showError(signUpErrorLabel, "Điền đủ thông tin đăng ký.");
            return;
        }

        //nối UserService.register().
        showError(signUpErrorLabel, "Chưa nối UserService.");
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
