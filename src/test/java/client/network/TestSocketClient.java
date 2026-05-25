package client.network;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TestSocketClient extends SocketClient {
    private final Map<String, Map<String, Object>> responsesByAction = new HashMap<>();
    private final List<String> syncActions = new ArrayList<>();
    private final List<String> asyncActions = new ArrayList<>();
    private final Map<String, Map<String, Object>> payloadsByAction = new HashMap<>();

    private boolean connectedCalled;
    private boolean closedCalled;

    public TestSocketClient() {
        super("127.0.0.1", 1);
    }

    public void setResponse(String action, Map<String, Object> response) {
        responsesByAction.put(action, response);
    }

    public List<String> getSyncActions() {
        return syncActions;
    }

    public List<String> getAsyncActions() {
        return asyncActions;
    }

    public Map<String, Object> getPayloadOf(String action) {
        return payloadsByAction.get(action);
    }

    public boolean wasConnectedCalled() {
        return connectedCalled;
    }

    public boolean wasClosedCalled() {
        return closedCalled;
    }

    @Override
    public void connect() {
        connectedCalled = true;
    }

    @Override
    public void close() {
        closedCalled = true;
    }

    @Override
    public Map<String, Object> sendRequest(String action, Map<String, Object> payload) {
        syncActions.add(action);
        if (payload != null) {
            payloadsByAction.put(action, new LinkedHashMap<>(payload));
        }
        Map<String, Object> response = responsesByAction.get(action);
        if (response == null) {
            return Map.of("status", "success");
        }
        return response;
    }

    @Override
    public void sendRequestAsync(String action, Map<String, Object> payload) {
        asyncActions.add(action);
        if (payload != null) {
            payloadsByAction.put(action, new LinkedHashMap<>(payload));
        }
    }
}
