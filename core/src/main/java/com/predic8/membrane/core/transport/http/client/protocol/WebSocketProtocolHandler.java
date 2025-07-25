package com.predic8.membrane.core.transport.http.client.protocol;

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.transport.http.*;

import static com.predic8.membrane.core.exchange.Exchange.ALLOW_WEBSOCKET;
import static java.lang.Boolean.TRUE;

public class WebSocketProtocolHandler  implements ProtocolHandler {

    @Override
    public boolean canHandle(Exchange exchange, String protocol) {
        return isWebsocketProtocolUpgradeAllowed(exchange, protocol);
    }

    @Override
    public Exchange handle(Exchange exchange, ConnectionFactory.OutgoingConnectionType connectionType, HostColonPort target) throws Exception {
        return null;
    }

    @Override
    public void checkUpgradeRequest(Exchange exchange) throws ProtocolUpgradeDeniedException {

    }

    @Override
    public String checkUpgradeResponse(Exchange exchange, Response response) {
        return "";
    }

    public static boolean isWebsocketProtocolUpgradeAllowed(Exchange exc, String upgradeProtocol) {
        if (upgradeProtocol == null) {
            return false;
        }
        return upgradeProtocol.equalsIgnoreCase("websocket") && exc.getProperty(ALLOW_WEBSOCKET) == TRUE;
    }
}
