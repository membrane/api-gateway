package com.predic8.membrane.core.transport.http.client.protocol;

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.transport.http.*;
import com.predic8.membrane.core.transport.http.client.*;
import com.predic8.membrane.core.transport.http2.*;

import static com.predic8.membrane.core.exchange.Exchange.*;
import static com.predic8.membrane.core.http.Header.*;
import static java.lang.Boolean.*;

public class Http2ProtocolHandler implements ProtocolHandler {

    public static final String HTTP2 = "h2";

    HttpClientConfiguration configuration;
    ConnectionFactory connectionFactory;

    public Http2ProtocolHandler(HttpClientConfiguration configuration, ConnectionFactory connectionFactory) {
        this.configuration = configuration;
        this.connectionFactory = connectionFactory;
    }

    @Override
    public boolean canHandle(Exchange exchange, String protocol) {
        return "h2c".equals(protocol) || /* TODO QF */ HTTP2.equals(protocol);
    }

    @Override
    public Exchange handle(Exchange exchange, ConnectionFactory.OutgoingConnectionType connectionType, HostColonPort target) throws Exception {
        Http2Client h2c = connectionType.h2c();
        if (h2c == null) {
            h2c = new Http2Client(connectionType.con(), connectionType.sslProvider().showSSLExceptions());
            Connection con = connectionType.con();

            connectionFactory.getHttp2ClientPool().share(con.getHost(),
                    con.socket.getPort(),
                    connectionType.sslProvider(),
                    connectionType.sniServerName(),
                    configuration.getProxy(),
                    connectionFactory.getProxySSLContext(),
                    h2c);
        }
        exchange.setResponse(h2c.doCall(exchange));
        exchange.setProperty(HTTP2, true);
        return exchange;
    }

    @Override
    public void checkUpgradeRequest(Exchange exchange) throws ProtocolUpgradeDeniedException {
        removeH2CUpgradeHeader(exchange);
    }

    @Override
    public String checkUpgradeResponse(Exchange exchange, Response response) {
        return "";
    }

    private static void removeH2CUpgradeHeader(Exchange exc) {
        if (exc.getProperty(ALLOW_H2) == TRUE) {
            // note that this has been deprecated by RFC9113 superseeding RFC7540, and therefore should not happen.
            return;
        }
        // RFC750 section 3.2 specifies that servers not supporting this can respond "as though the Upgrade header
        // field were absent". Therefore, we remove it.
//        if (infoOnHttp2Downgrade) {
//            infoOnHttp2Downgrade = false;
//            log.info("Your client sent a 'Connection: Upgrade' with 'Upgrade: h2c'. Please note that RFC7540 has " +
//                     "been superseeded by RFC9113, which removes this option. The header was and will be removed.");
//        }

        exc.getRequest().getHeader().removeFields(UPGRADE);
        exc.getRequest().getHeader().removeFields(HTTP2_SETTINGS);
        exc.getRequest().getHeader().keepOnly(CONNECTION, value -> !value.equalsIgnoreCase(UPGRADE) && !value.equalsIgnoreCase(HTTP2_SETTINGS));
    }
}
