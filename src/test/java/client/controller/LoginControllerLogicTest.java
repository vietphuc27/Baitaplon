package client.controller;

import client.application.ClientSession;
import client.network.TestSocketClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class LoginControllerLogicTest {

    @BeforeEach
    void setUp() throws Exception {
        Field field = ClientSession.class.getDeclaredField("sharedSocket");
        field.setAccessible(true);
        field.set(null, new TestSocketClient());
    }

    @AfterEach
    void tearDown() {
        ClientSession.clear();
    }

    @Test
    void constructorInitializesWithoutJavaFxControls() {
        assertNotNull(new LoginController());
    }
}
