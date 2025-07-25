package com.predic8.membrane.core.transport.http.client.protocol;

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.transport.http.*;

public interface ProtocolHandler {
    default boolean canHandle(Exchange exchange, String protocol) throws ProtocolUpgradeDeniedException {
        throw new ProtocolUpgradeDeniedException(protocol);
    }

    Exchange handle(Exchange exchange, ConnectionFactory.OutgoingConnectionType connectionType, HostColonPort target) throws Exception;

    void checkUpgradeRequest(Exchange exchange) throws ProtocolUpgradeDeniedException;
    String checkUpgradeResponse(Exchange exchange, Response response);
}