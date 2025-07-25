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
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.transport.http.*;
import com.predic8.membrane.core.transport.http.ConnectionFactory.*;
import com.predic8.membrane.core.transport.http.client.*;
import com.predic8.membrane.core.util.*;
import org.slf4j.Logger;
import org.slf4j.*;

import java.io.*;

import static com.predic8.membrane.core.Constants.*;
import static com.predic8.membrane.core.exchange.Exchange.*;
import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.http.Request.*;
import static com.predic8.membrane.core.http.Response.*;
import static com.predic8.membrane.core.transport.http.client.protocol.TcpProtocolHandler.*;
import static com.predic8.membrane.core.transport.http.client.protocol.WebSocketProtocolHandler.*;
import static java.lang.Boolean.*;
import static java.lang.System.*;

public class Http1ProtocolHandler implements ProtocolHandler {

    private static final Logger log = LoggerFactory.getLogger(Http1ProtocolHandler.class.getName());

    private final HttpClientConfiguration configuration;

    public Http1ProtocolHandler(HttpClientConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public Exchange handle(Exchange exchange, OutgoingConnectionType connectionType, HostColonPort target) throws Exception {
        Connection con = connectionType.con();

        if (exchange.getRequest().isCONNECTRequest()) {
            if (configuration.getProxy() != null) {
                exchange.getRequest().write(con.out, configuration.getMaxRetries() > 1);
                Response response = fromStream(con.in, false);
                log.debug("Status code response? on CONNECT request: {}", response.getStatusCode());
            }
            exchange.getRequest().setUri(NOT_APPLICABLE);
            exchange.setResponse(ok().build());
            exchange.setProperty(UPGRADED_PROTOCOL, METHOD_CONNECT);
            return exchange;
        }

        // TODO only for HTTP1 ?
        con.socket.setSoTimeout(configuration.getConnection().getSoTimeout());

        exchange.getRequest().write(con.out, configuration.getMaxRetries() > 1);

        // TODO only for HTTP1 ?
        exchange.setTimeReqSent(currentTimeMillis());

        if (exchange.getRequest().isHTTP10()) {
            shutDownRequestInputOutput(exchange, con);
        }

        var response = fromStream(con.in, !exchange.getRequest().isHEADRequest());

        if (response.getStatusCode() == 100) {
            do100ExpectedHandling(exchange, response, con);
        }

        exchange.setReceived();
        exchange.setTimeResReceived(currentTimeMillis());

        exchange.setResponse(response);

        checkUpgradeResponse(exchange);

        return exchange;
    }

    @Override
    public void checkUpgradeResponse(Exchange exchange) {

        if (isUpgradeToResponse(exchange.getResponse(), WEBSOCKET) &&
            exchange.getProperty(ALLOW_WEBSOCKET) == TRUE) {
            exchange.setProperty(UPGRADED_PROTOCOL, "WebSocket"); // TODO casing, constant
            return;
        }
        if (isUpgradeToResponse(exchange.getResponse(), TCP) &&
            exchange.getProperty(ALLOW_TCP) == TRUE) {
            exchange.setProperty(UPGRADED_PROTOCOL, "TCP"); // TODO casing, constant
        }
    }

    @Override
    public void cleanup(Exchange exchange) {
        if (exchange.getRequest().isCONNECTRequest())
            return;

        Connection tc = exchange.getTargetConnection();
        if (tc == null)
            return;

        applyKeepAliveHeader(exchange.getResponse(), tc);
        exchange.getResponse().addObserver(tc);
        tc.setExchange(exchange);

    }

    private void applyKeepAliveHeader(Response response, Connection con) {
        String value = response.getHeader().getFirstValue(KEEP_ALIVE);
        if (value == null)
            return;

        long timeoutSeconds = Header.parseKeepAliveHeader(value, TIMEOUT);
        if (timeoutSeconds != -1)
            con.setTimeout(timeoutSeconds * 1000);

        long max = Header.parseKeepAliveHeader(value, MAX);
        if (max != -1 && max < con.getMaxExchanges())
            con.setMaxExchanges((int) max);
    }

    private boolean isUpgradeToResponse(Response res, String protocol) {
        return res.getStatusCode() == 101 &&
               "upgrade".equalsIgnoreCase(res.getHeader().getFirstValue(CONNECTION)) &&
               protocol.equalsIgnoreCase(res.getHeader().getFirstValue(UPGRADE));
    }

    private void shutDownRequestInputOutput(Exchange exc, Connection con) throws IOException {
        exc.getHandler().shutdownInput();
        Util.shutdownOutput(con.socket);
    }

    private void do100ExpectedHandling(Exchange exc, Response response, Connection con) throws IOException, EndOfStreamException {
        exc.getRequest().getBody().write(exc.getRequest().getHeader().isChunked() ? new ChunkedBodyTransferrer(con.out) : new PlainBodyTransferrer(con.out), configuration.getMaxRetries() > 1);
        con.out.flush();
        response.read(con.in, !exc.getRequest().isHEADRequest());
    }
}
