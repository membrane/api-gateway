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
import org.jetbrains.annotations.*;
import org.slf4j.Logger;
import org.slf4j.*;

import java.io.*;

import static com.predic8.membrane.core.Constants.*;
import static com.predic8.membrane.core.exceptions.ProblemDetails.*;
import static com.predic8.membrane.core.exchange.Exchange.*;
import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.http.Request.*;
import static com.predic8.membrane.core.http.Response.*;
import static com.predic8.membrane.core.transport.http.client.protocol.TcpProtocolHandler.*;
import static com.predic8.membrane.core.transport.http.client.protocol.WebSocketProtocolHandler.*;
import static java.lang.Boolean.*;
import static java.lang.System.*;

public class Http1ProtocolHandler extends AbstractProtocolHandler {

    private static final Logger log = LoggerFactory.getLogger(Http1ProtocolHandler.class.getName());

    ResponseReader responseReader = (ext, ct) -> fromStream(ct.con().in, !ext.getRequest().isHEADRequest());

    public Http1ProtocolHandler(HttpClientConfiguration hcc, ConnectionFactory cf) {
        super(hcc, cf);
    }

    @Override
    public Exchange handle(Exchange exchange, OutgoingConnectionType ct, HostColonPort target) throws Exception {

        // 100 - Continue
        var connectExchange = answerConnectRequest(exchange, ct);
        if (connectExchange != null) return connectExchange;

        // TODO only for HTTP1 ?
        ct.con().socket.setSoTimeout(configuration.getConnection().getSoTimeout());

        exchange.getRequest().write(ct.con().out, configuration.getMaxRetries() > 1);

        // TODO only for HTTP1 ?
        exchange.setTimeReqSent(currentTimeMillis());

        http10ShutDown(exchange, ct.con());

        exchange.setResponse(responseReader.read(exchange, ct));

        // 100 - Continue
        handle100Expected(exchange, ct.con());

        // Only HTTP 1?
        exchange.setReceived();
        exchange.setTimeResReceived(currentTimeMillis());

        // Only HTTP 1?
        checkUpgradeResponse(exchange);

        return exchange;
    }

    @Override
    public void checkUpgradeResponse(Exchange exchange) {
        if (isUpgradeToResponse(exchange.getResponse(), WEBSOCKET) &&
            exchange.getProperty(ALLOW_WEBSOCKET) == TRUE) {
            exchange.setProperty(UPGRADED_PROTOCOL, WEBSOCKET);
            return;
        }
        if (isUpgradeToResponse(exchange.getResponse(), TCP) &&
            exchange.getProperty(ALLOW_TCP) == TRUE) {
            exchange.setProperty(UPGRADED_PROTOCOL, TCP);
        }
    }

    private boolean isUpgradeToResponse(Response res, String protocol) {
        return res.getStatusCode() == 101 &&
               UPGRADE.equalsIgnoreCase(res.getHeader().getFirstValue(CONNECTION)) &&
               protocol.equalsIgnoreCase(res.getHeader().getFirstValue(UPGRADE));
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

    private void http10ShutDown(Exchange exchange, Connection connection) throws IOException {
        if (!exchange.getRequest().isHTTP10())
            return;
        exchange.getHandler().shutdownInput();
        Util.shutdownOutput(connection.socket);
    }

    // 100 - Connect

    private void handle100Expected(Exchange exchange, Connection c) throws IOException, EndOfStreamException {
        Response response = exchange.getResponse();
        if (response.getStatusCode() != 100)
            return;
        exchange.getRequest().getBody().write(getBodyTransferer(exchange, c), configuration.getMaxRetries() > 1);
        c.out.flush();
        response.read(c.in, !exchange.getRequest().isHEADRequest());
    }

    private static @NotNull AbstractBodyTransferrer getBodyTransferer(Exchange exchange, Connection c) {
        return exchange.getRequest().getHeader().isChunked() ? new ChunkedBodyTransferer(c.out) : new PlainBodyTransferer(c.out);
    }

    private @Nullable Exchange answerConnectRequest(Exchange exchange, OutgoingConnectionType ct) throws IOException, EndOfStreamException {
        if (!exchange.getRequest().isCONNECTRequest())
            return null;

        if (configuration.getProxy() != null) {
            exchange.getRequest().write(ct.con().out, configuration.getMaxRetries() > 1);
            Response response = fromStream(ct.con().in, false);
            if (response.getStatusCode() > 299) {
                log.debug("Status code response? on CONNECT request: {}", response.getStatusCode());
                exchange.setResponse(internal(true, "proxy")
                        .detail("Could not connect to proxy server")
                        .build());
            }
        }
        exchange.getRequest().setUri(NOT_APPLICABLE); // TODO Why?
        exchange.setResponse(ok().build());
        exchange.setProperty(UPGRADED_PROTOCOL, METHOD_CONNECT);
        return exchange;
    }
}
