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
import com.predic8.membrane.core.transport.http.client.*;
import com.predic8.membrane.core.transport.http2.*;
import org.slf4j.*;

import static com.predic8.membrane.core.exchange.Exchange.*;
import static com.predic8.membrane.core.http.Header.*;
import static java.lang.Boolean.*;

public class Http2ProtocolHandler implements ProtocolHandler {

    private static final Logger log = LoggerFactory.getLogger(Http2ProtocolHandler.class);

    public static final String HTTP2 = "h2";

    public static final String HTTP2_CLEAR = "h2c";

    private static volatile boolean infoOnHttp2Downgrade = true;

    private final HttpClientConfiguration configuration;
    private final ConnectionFactory connectionFactory;

    public Http2ProtocolHandler(HttpClientConfiguration configuration, ConnectionFactory connectionFactory) {
        this.configuration = configuration;
        this.connectionFactory = connectionFactory;
    }

    @Override
    public boolean canHandle(Exchange exchange, String protocol) {
        return HTTP2_CLEAR.equals(protocol) || HTTP2.equals(protocol);
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
    public void checkUpgradeRequest(Exchange exchange) {
        removeH2CUpgradeHeader(exchange);
    }

    public static boolean isHTTP2(Exchange exc) {
        return !(exc.getProperty(HTTP2) == null || (exc.getProperty(HTTP2) instanceof Boolean h2 && !h2));
    }

    private static void removeH2CUpgradeHeader(Exchange exc) {
        if (exc.getProperty(ALLOW_H2) == TRUE) {
            // note that this has been deprecated by RFC9113 superseeding RFC7540, and therefore should not happen.
            return;
        }
        if (!isH2CUpgradeRequest(exc)) {
            return;
        }
        // RFC750 section 3.2 specifies that servers not supporting this can respond "as though the Upgrade header
        // field were absent". Therefore, we remove it.
        if (infoOnHttp2Downgrade) {
            infoOnHttp2Downgrade = false;
            log.info("Your client sent a 'Connection: Upgrade' with 'Upgrade: h2c'. Please note that RFC7540 has " +
                     "been superseeded by RFC9113, which removes this option. The header was and will be removed.");
        }
        exc.getRequest().getHeader().removeFields(UPGRADE);
        exc.getRequest().getHeader().removeFields(HTTP2_SETTINGS);
        exc.getRequest().getHeader().keepOnly(CONNECTION, value -> !value.equalsIgnoreCase(UPGRADE) && !value.equalsIgnoreCase(HTTP2_SETTINGS));
    }

    private static boolean isH2CUpgradeRequest(Exchange exchange) {
        String upgrade = exchange.getRequest().getHeader().getFirstValue(UPGRADE);
        return upgrade != null && upgrade.equalsIgnoreCase(HTTP2_CLEAR);
    }
}
