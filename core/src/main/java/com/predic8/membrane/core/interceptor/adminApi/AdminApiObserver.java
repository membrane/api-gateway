package com.predic8.membrane.core.interceptor.adminApi;

import com.predic8.membrane.core.transport.ws.WebSocketConnection;
import com.predic8.membrane.core.transport.ws.WebSocketFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AdminApiObserver extends WebSocketConnection {

    private static final Logger log = LoggerFactory.getLogger(AdminApiObserver.class);

    @Override
    public void onMessage(WebSocketFrame frame) {
        log.info("Received message from client: " + frame);
    }
}
