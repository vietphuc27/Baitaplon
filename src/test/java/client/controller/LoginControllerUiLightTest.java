package client.controller;

import client.application.ClientSession;
import client.network.TestSocketClient;
import javafx.event.ActionEvent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginControllerUiLightTest extends JavaFxTestSupport {

    @BeforeEach
    void setUp() throws Exception {
        injectSharedSocket(new TestSocketClient());
    }

    @AfterEach
    void tearDown() {
        ClientSession.clear();
    }

    @Test
    void initializeTabSwitchAndEmptyValidationWorkWithoutStage() throws Exception {
        runOnFxThread(() -> {
            LoginController controller = new LoginController();

            TextField usernameField = new TextField();
            PasswordField passwordField = new PasswordField();
            Button loginBtn = new Button();
            Label errorLabel = new Label("old");
            errorLabel.setVisible(true);

            TextField signUpUsernameField = new TextField();
            TextField emailField = new TextField();
            ComboBox<String> roleComboBox = new ComboBox<>();
            PasswordField signUpPasswordField = new PasswordField();
            Button registerBtn = new Button();
            Label signUpErrorLabel = new Label("old");
            signUpErrorLabel.setVisible(true);

            TabPane authTabPane = new TabPane();
            Tab signInTab = new Tab("Sign in");
            Tab signUpTab = new Tab("Sign up");
            authTabPane.getTabs().addAll(signInTab, signUpTab);

            setField(controller, "usernameField", usernameField);
            setField(controller, "passwordField", passwordField);
            setField(controller, "loginBtn", loginBtn);
            setField(controller, "errorLabel", errorLabel);
            setField(controller, "signUpUsernameField", signUpUsernameField);
            setField(controller, "emailField", emailField);
            setField(controller, "roleComboBox", roleComboBox);
            setField(controller, "signUpPasswordField", signUpPasswordField);
            setField(controller, "registerBtn", registerBtn);
            setField(controller, "signUpErrorLabel", signUpErrorLabel);
            setField(controller, "authTabPane", authTabPane);
            setField(controller, "signInTab", signInTab);
            setField(controller, "signUpTab", signUpTab);

            invoke(controller, "initialize", new Class<?>[0]);
            assertEquals(2, roleComboBox.getItems().size());
            assertEquals("", errorLabel.getText());
            assertFalse(errorLabel.isVisible());
            assertEquals("", signUpErrorLabel.getText());
            assertFalse(signUpErrorLabel.isVisible());
            assertTrue(loginBtn.isDefaultButton());

            invoke(controller, "goToSignUp", new Class<?>[] { ActionEvent.class }, new ActionEvent());
            assertEquals(signUpTab, authTabPane.getSelectionModel().getSelectedItem());
            assertTrue(registerBtn.isDefaultButton());
            assertFalse(loginBtn.isDefaultButton());

            invoke(controller, "goToSignIn", new Class<?>[] { ActionEvent.class }, new ActionEvent());
            assertEquals(signInTab, authTabPane.getSelectionModel().getSelectedItem());
            assertTrue(loginBtn.isDefaultButton());
            assertFalse(registerBtn.isDefaultButton());

            invoke(controller, "handleLogin", new Class<?>[] { ActionEvent.class }, new ActionEvent());
            assertEquals("Nhập username và password.", errorLabel.getText());
            assertTrue(errorLabel.isVisible());

            invoke(controller, "handleRegister", new Class<?>[] { ActionEvent.class }, new ActionEvent());
            assertEquals("Điền đủ thông tin đăng ký.", signUpErrorLabel.getText());
            assertTrue(signUpErrorLabel.isVisible());
            return null;
        });
    }

    private void injectSharedSocket(TestSocketClient socketClient) throws Exception {
        Field field = ClientSession.class.getDeclaredField("sharedSocket");
        field.setAccessible(true);
        field.set(null, socketClient);
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private Object invoke(Object target, String methodName, Class<?>[] paramTypes, Object... args) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName, paramTypes);
        method.setAccessible(true);
        return method.invoke(target, args);
    }
}
