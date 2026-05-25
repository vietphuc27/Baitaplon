package client.controller;

import client.application.ClientSession;
import client.network.TestSocketClient;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginControllerLogicTest {

    @BeforeAll
    static void initFxToolkit() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException alreadyStarted) {
            latch.countDown();
        }
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    @BeforeEach
    void setUp() throws Exception {
        injectSharedSocket(new TestSocketClient());
    }

    @AfterEach
    void tearDown() {
        ClientSession.clear();
    }

    @Test
    void initializeAndTabSwitchWork() throws Exception {
        runFxAndWait(() -> {
            LoginController controller = new LoginController();
            wireUi(controller);
            invoke(controller, "initialize", new Class<?>[0]);

            ComboBox<?> roleCombo = (ComboBox<?>) field(controller, "roleComboBox");
            Button loginBtn = (Button) field(controller, "loginBtn");
            Button registerBtn = (Button) field(controller, "registerBtn");
            Label errorLabel = (Label) field(controller, "errorLabel");
            Label signUpErrorLabel = (Label) field(controller, "signUpErrorLabel");
            TabPane authTabPane = (TabPane) field(controller, "authTabPane");
            Tab signInTab = (Tab) field(controller, "signInTab");
            Tab signUpTab = (Tab) field(controller, "signUpTab");

            assertEquals(List.of("Seller", "Bidder"), roleCombo.getItems());
            assertTrue(loginBtn.isDefaultButton());
            assertFalse(errorLabel.isVisible());
            assertFalse(signUpErrorLabel.isVisible());

            invoke(controller, "goToSignUp", new Class<?>[] { ActionEvent.class }, new ActionEvent());
            assertEquals(signUpTab, authTabPane.getSelectionModel().getSelectedItem());
            assertTrue(registerBtn.isDefaultButton());
            assertFalse(loginBtn.isDefaultButton());

            invoke(controller, "goToSignIn", new Class<?>[] { ActionEvent.class }, new ActionEvent());
            assertEquals(signInTab, authTabPane.getSelectionModel().getSelectedItem());
            assertTrue(loginBtn.isDefaultButton());
            assertFalse(registerBtn.isDefaultButton());
        });
    }

    @Test
    void handleLoginShowsValidationAndServerErrors() throws Exception {
        runFxAndWait(() -> {
            TestSocketClient socket = (TestSocketClient) ClientSession.getSocket();
            socket.setResponse("login", Map.of("status", "error", "message", "Sai tai khoan"));

            LoginController controller = new LoginController();
            wireUi(controller);
            invoke(controller, "initialize", new Class<?>[0]);

            TextField username = (TextField) field(controller, "usernameField");
            PasswordField password = (PasswordField) field(controller, "passwordField");
            Label error = (Label) field(controller, "errorLabel");

            invoke(controller, "handleLogin", new Class<?>[] { ActionEvent.class }, new Object[] { null });
            assertEquals("Nhập username và password.", error.getText());
            assertTrue(error.isVisible());

            username.setText("alice");
            password.setText("wrong");
            invoke(controller, "handleLogin", new Class<?>[] { ActionEvent.class }, new Object[] { null });
            assertEquals("Sai tai khoan", error.getText());
            assertTrue(error.isVisible());
        });
    }

    @Test
    void handleRegisterCoversValidationSuccessAndFailure() throws Exception {
        runFxAndWait(() -> {
            TestSocketClient socket = (TestSocketClient) ClientSession.getSocket();
            socket.setResponse("register", Map.of(
                    "status", "success",
                    "userId", 12,
                    "username", "newuser",
                    "email", "new@e",
                    "password", "x",
                    "role", "BIDDER"));

            LoginController controller = new LoginController();
            wireUi(controller);
            invoke(controller, "initialize", new Class<?>[0]);

            TextField signUpUsername = (TextField) field(controller, "signUpUsernameField");
            TextField email = (TextField) field(controller, "emailField");
            PasswordField signUpPassword = (PasswordField) field(controller, "signUpPasswordField");
            ComboBox<String> roleCombo = castCombo(field(controller, "roleComboBox"));
            Label signUpError = (Label) field(controller, "signUpErrorLabel");

            invoke(controller, "handleRegister", new Class<?>[] { ActionEvent.class }, new Object[] { null });
            assertEquals("Điền đủ thông tin đăng ký.", signUpError.getText());
            assertTrue(signUpError.isVisible());

            signUpUsername.setText("newuser");
            email.setText("new@e");
            signUpPassword.setText("pw");
            roleCombo.setValue("Bidder");
            invoke(controller, "handleRegister", new Class<?>[] { ActionEvent.class }, new Object[] { null });

            assertTrue(signUpError.getText().contains("UserID: 12"));
            assertEquals("", signUpUsername.getText());
            assertEquals("", email.getText());
            assertEquals("", signUpPassword.getText());
            assertNull(roleCombo.getValue());

            socket.setResponse("register", Map.of("status", "error", "message", "Trung username"));
            signUpUsername.setText("newuser");
            email.setText("new@e");
            signUpPassword.setText("pw");
            roleCombo.setValue("Bidder");
            invoke(controller, "handleRegister", new Class<?>[] { ActionEvent.class }, new Object[] { null });
            assertEquals("Trung username", signUpError.getText());
            assertTrue(signUpError.isVisible());
        });
    }

    @SuppressWarnings("unchecked")
    private ComboBox<String> castCombo(Object value) {
        return (ComboBox<String>) value;
    }

    private void wireUi(LoginController controller) throws Exception {
        setField(controller, "usernameField", new TextField());
        setField(controller, "passwordField", new PasswordField());
        setField(controller, "loginBtn", new Button());
        setField(controller, "errorLabel", new Label());
        setField(controller, "signUpUsernameField", new TextField());
        setField(controller, "emailField", new TextField());
        setField(controller, "roleComboBox", new ComboBox<String>());
        setField(controller, "signUpPasswordField", new PasswordField());
        setField(controller, "registerBtn", new Button());
        setField(controller, "signUpErrorLabel", new Label());

        TabPane authTabPane = new TabPane();
        Tab signInTab = new Tab("Sign In");
        Tab signUpTab = new Tab("Sign Up");
        authTabPane.getTabs().addAll(signInTab, signUpTab);
        authTabPane.getSelectionModel().select(signInTab);

        setField(controller, "authTabPane", authTabPane);
        setField(controller, "signInTab", signInTab);
        setField(controller, "signUpTab", signUpTab);
    }

    private Object invoke(Object target, String method, Class<?>[] paramTypes, Object... args) throws Exception {
        Method m = target.getClass().getDeclaredMethod(method, paramTypes);
        m.setAccessible(true);
        return m.invoke(target, args);
    }

    private Object field(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private void injectSharedSocket(TestSocketClient socketClient) throws Exception {
        Field field = ClientSession.class.getDeclaredField("sharedSocket");
        field.setAccessible(true);
        field.set(null, socketClient);
    }

    private void runFxAndWait(ThrowingRunnable runnable) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                runnable.run();
            } catch (Throwable t) {
                error.set(t);
            } finally {
                latch.countDown();
            }
        });
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        if (error.get() != null) {
            throw new RuntimeException(error.get());
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}

