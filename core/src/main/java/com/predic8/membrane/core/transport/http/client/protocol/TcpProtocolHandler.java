package com.predic8.membrane.core.transport.http.client.protocol;

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.transport.http.*;

import static com.predic8.membrane.core.exchange.Exchange.ALLOW_TCP;
import static java.lang.Boolean.TRUE;

public class TcpProtocolHandler implements ProtocolHandler {
    @Override
    public boolean canHandle(Exchange exchange, String protocol) throws ProtocolUpgradeDeniedException {
        return isTcpProtocolUpgradeAllowed(exchange, protocol);
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

    public static boolean isTcpProtocolUpgradeAllowed(Exchange exc, String upgradeProtocol) {
        if (upgradeProtocol == null) {
            return false;
        }
        return upgradeProtocol.equalsIgnoreCase("tcp") && exc.getProperty(ALLOW_TCP) == TRUE;
    }
}
