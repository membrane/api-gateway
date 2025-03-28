/* Copyright 2025 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
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
