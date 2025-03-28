package com.predic8.membrane.core.transport.ws;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Collects a bunch of active {@link WebSocketConnection} instances.
 * <p>
 * Supports broadcasting of JSON messages.
 */
public class WebSocketConnectionCollection {
    private List<WebSocketConnection> connections = new ArrayList<>();
    private ObjectMapper om = new ObjectMapper();

    /**
     * Sends the 'data' as a JSON object to all connected WebSocket listeners.
     */
    public void broadcast(Map data) throws JsonProcessingException {
        String msg = om.writeValueAsString(data);

        ArrayList<WebSocketConnection> connectionsToNotify;
        synchronized (connections) {
            connectionsToNotify = new ArrayList<>(connections);
        }
        for (WebSocketConnection connection : connectionsToNotify) {
            connection.enqueueForSending(msg);
        }
    }

    public void register(WebSocketConnection connection) {
        synchronized (connections) {
            connections.add(connection);
        }
    }

    public void unregister(WebSocketConnection connection) {
        synchronized (connections) {
            connections.remove(connection);
        }
    }
}
