package com.predic8.membrane.core.transport.http.client.protocol;

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.transport.http.*;
import com.predic8.membrane.core.transport.http.client.*;

import java.util.*;

public class ProtocolHandlerFactory {
    private final List<ProtocolHandler> handlers;
    private final Http1ProtocolHandler defaultHandler;

    public ProtocolHandlerFactory(HttpClientConfiguration configuration, ConnectionFactory connectionFactory) {
        this.defaultHandler = new Http1ProtocolHandler(configuration);
        this.handlers = Arrays.asList(
                new Http2ProtocolHandler(configuration,connectionFactory),
                new WebSocketProtocolHandler(),
                new TcpProtocolHandler()
            //    new Http1ProtocolHandler(configuration) // Default fallback
        );
    }

    public ProtocolHandler getHandler(Exchange exchange, String protocol) throws ProtocolUpgradeDeniedException {

        if (protocol == null) {
            return defaultHandler;
        }

        // Use no stream API because of Exception handling
        for (ProtocolHandler h : handlers) {
            if (h.canHandle(exchange, protocol)) {
                return h; // HTTP/1.1 as default
            }
        }

        throw new ProtocolUpgradeDeniedException(protocol);
    }

    public void checkUpgradeRequests(Exchange exchange) throws ProtocolUpgradeDeniedException {
        for (ProtocolHandler handler : handlers) {
            handler.checkUpgradeRequest(exchange);
        }
    }
}