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

import static com.predic8.membrane.core.exchange.Exchange.*;
import static java.lang.Boolean.*;

public class WebSocketProtocolHandler  implements ProtocolHandler {

    public static final String WEBSOCKET = "websocket";

    @Override
    public boolean canHandle(Exchange exchange, String protocol) {
        return isWebsocketProtocolUpgradeAllowed(exchange, protocol);
    }

    @Override
    public Exchange handle(Exchange exchange, ConnectionFactory.OutgoingConnectionType connectionType, HostColonPort target) throws Exception {
        throw new IllegalStateException(
                "WebSocket handler should not be called directly. " +
                "Use HTTP/1.1 with Upgrade header.");
    }

    @Override
    public void checkUpgradeRequest(Exchange exchange) throws ProtocolUpgradeDeniedException {
        String protocol = exchange.getRequest().getHeader().getUpgradeProtocol();
        if (WEBSOCKET.equalsIgnoreCase(protocol) &&
            !isWebsocketProtocolUpgradeAllowed(exchange, protocol)) {
            throw new ProtocolUpgradeDeniedException(protocol);
        }
    }

    public static boolean isWebsocketProtocolUpgradeAllowed(Exchange exc, String upgradeProtocol) {
        if (upgradeProtocol == null) {
            return false;
        }
        return upgradeProtocol.equalsIgnoreCase(WEBSOCKET) && exc.getProperty(ALLOW_WEBSOCKET) == TRUE;
    }
}
