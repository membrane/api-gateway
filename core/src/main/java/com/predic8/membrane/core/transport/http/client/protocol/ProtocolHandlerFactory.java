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

package com.predic8.membrane.core.transport.http.client.protocol;

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.transport.http.*;
import com.predic8.membrane.core.transport.http.ConnectionFactory.*;
import com.predic8.membrane.core.transport.http.client.*;

import java.util.*;

import static java.util.Arrays.asList;

public class ProtocolHandlerFactory {
    private final List<ProtocolHandler> handlers;
    private final Http1ProtocolHandler defaultHandler;

    public ProtocolHandlerFactory(HttpClientConfiguration configuration, ConnectionFactory connectionFactory) {
        this.defaultHandler = new Http1ProtocolHandler(configuration);
        this.handlers = asList(
                new Http2ProtocolHandler(configuration,connectionFactory),
                new WebSocketProtocolHandler(),
                new TcpProtocolHandler()
        );
    }

    public ProtocolHandler getHandler(Exchange exchange, String protocol) throws ProtocolUpgradeDeniedException {

        if (protocol == null) {
            return defaultHandler;
        }

        // Use no stream API because of Exception handling
        for (ProtocolHandler h : handlers) {
            if (h.canHandle(exchange, protocol)) {
                return h;
            }
        }

        throw new ProtocolUpgradeDeniedException(protocol);
    }

    public ProtocolHandler getHandlerForConnection(Exchange exchange, OutgoingConnectionType connectionType)
            throws ProtocolUpgradeDeniedException {
        if (connectionType.usingHttp2()) {
            return getHandler(exchange, "h2");
        }
        return getHandler(exchange, null);
    }
}