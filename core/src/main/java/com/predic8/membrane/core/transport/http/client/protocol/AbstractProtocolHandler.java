package com.predic8.membrane.core.transport.http.client.protocol;

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.transport.http.*;
import com.predic8.membrane.core.transport.http.client.*;

public abstract class AbstractProtocolHandler implements ProtocolHandler {

    protected final HttpClientConfiguration configuration;
    protected final ConnectionFactory connectionFactory;

    // TODO protected
    public static final String UPGRADED_PROTOCOL = "UPGRADED_PROTOCOL";

    public AbstractProtocolHandler(HttpClientConfiguration configuration, ConnectionFactory connectionFactory) {
        this.configuration = configuration;
        this.connectionFactory = connectionFactory;
    }

    @Override
    public boolean canHandle(Exchange exchange, String protocol) {
        return false;
    }

    @Override
    public void checkUpgradeRequest(Exchange exchange) throws ProtocolUpgradeDeniedException {}

    @Override
    public void checkUpgradeResponse(Exchange exchange) {}

    @Override
    public void cleanup(Exchange exchange) {}

    protected static boolean isUpgradeRequest(Exchange exchange, String protocol) {
        String upgrade = exchange.getRequest().getHeader().getUpgradeProtocol();
        return upgrade != null && upgrade.equalsIgnoreCase(protocol);
    }
}
