package server.network;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import server.manager.ConnectionManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

public class ClientHandlerTest {

    @AfterEach
    public void tearDown() {
        ConnectionManager.getInstance().disconnectAll();
    }

    @Test
    public void constructorValidatesArguments() {
        RequestHandler req = new RequestHandler();
        ConnectionManager cm = ConnectionManager.getInstance();

        assertThrows(IllegalArgumentException.class, () -> new ClientHandler(null, req, cm));
        assertThrows(IllegalArgumentException.class, () -> new ClientHandler(new Socket(), null, cm));
        assertThrows(IllegalArgumentException.class, () -> new ClientHandler(new Socket(), req, null));
    }

    @Test
    public void runSendsHandshakeProcessesRequestAndCleansUp() throws Exception {
        ConnectionManager cm = ConnectionManager.getInstance();

        try (ServerSocket serverSocket = new ServerSocket(0);
             Socket clientSide = new Socket("127.0.0.1", serverSocket.getLocalPort());
             Socket serverSide = serverSocket.accept();
             BufferedReader clientReader = new BufferedReader(new InputStreamReader(clientSide.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter clientWriter = new PrintWriter(clientSide.getOutputStream(), true, StandardCharsets.UTF_8)) {

            ClientHandler handler = new ClientHandler(serverSide, new StubRequestHandler("{\"status\":\"ok\"}"), cm);
            Thread thread = new Thread(handler, "client-handler-test");
            thread.start();

            String handshake = clientReader.readLine();
            assertNotNull(handshake);
            assertTrue(handshake.contains("\"status\":\"connected\""));

            clientWriter.println("{\"action\":\"ping\"}");
            String response = clientReader.readLine();
            assertEquals("{\"status\":\"ok\"}", response);

            assertNotNull(handler.getClientId());
            assertTrue(handler.isConnected() || !handler.isConnected());

            clientSide.close();
            thread.join(2000);
            assertFalse(thread.isAlive());
            assertEquals(0, cm.getClientCount());
        }
    }

    @Test
    public void runReturnsSystemErrorWhenRequestHandlerThrows() throws Exception {
        ConnectionManager cm = ConnectionManager.getInstance();

        try (ServerSocket serverSocket = new ServerSocket(0);
             Socket clientSide = new Socket("127.0.0.1", serverSocket.getLocalPort());
             Socket serverSide = serverSocket.accept();
             BufferedReader clientReader = new BufferedReader(new InputStreamReader(clientSide.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter clientWriter = new PrintWriter(clientSide.getOutputStream(), true, StandardCharsets.UTF_8)) {

            ClientHandler handler = new ClientHandler(serverSide, new StubRequestHandler(new RuntimeException("boom")), cm);
            Thread thread = new Thread(handler, "client-handler-test-error");
            thread.start();

            assertNotNull(clientReader.readLine()); // handshake
            clientWriter.println("{\"action\":\"boom\"}");
            String response = clientReader.readLine();
            assertNotNull(response);
            assertTrue(response.contains("\"status\":\"error\""));

            thread.join(2000);
            assertFalse(thread.isAlive());
        }
    }

    @Test
    public void authenticationAndCloseHelpersWork() {
        ClientHandler handler = new ClientHandler(new Socket(), new RequestHandler(), ConnectionManager.getInstance());

        assertFalse(handler.isAuthenticated());
        handler.markAuthenticated(99, "token-99");
        assertTrue(handler.isAuthenticated());
        assertEquals(99, handler.getUserId());
        assertEquals("token-99", handler.getAuthToken());

        handler.clearAuthentication();
        assertFalse(handler.isAuthenticated());

        handler.close();
        handler.close();
        assertFalse(handler.isConnected());
    }

    private static final class StubRequestHandler extends RequestHandler {
        private final String fixedResponse;
        private final RuntimeException error;

        private StubRequestHandler(String fixedResponse) {
            this.fixedResponse = fixedResponse;
            this.error = null;
        }

        private StubRequestHandler(RuntimeException error) {
            this.fixedResponse = null;
            this.error = error;
        }

        @Override
        public String handle(String rawRequest, ClientHandler clientHandler) {
            if (error != null) {
                throw error;
            }
            return fixedResponse;
        }
    }
}
