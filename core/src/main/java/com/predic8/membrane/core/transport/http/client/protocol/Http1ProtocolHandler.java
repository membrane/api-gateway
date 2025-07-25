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
import org.slf4j.*;
import org.slf4j.Logger;

import java.io.*;

import static com.predic8.membrane.core.Constants.*;
import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.http.Request.METHOD_CONNECT;
import static com.predic8.membrane.core.http.Response.*;
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
            handleConnectRequest(exchange, con);
            exchange.setResponse(ok().build());
            exchange.setProperty("UPGRADED_PROTOCOL", METHOD_CONNECT);
            return exchange;
        }

        con.socket.setSoTimeout(configuration.getConnection().getSoTimeout());
        exchange.getRequest().write(con.out, configuration.getMaxRetries() > 1);
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

        String upgradedProtocol = checkUpgradeResponse(exchange, response);
        if (upgradedProtocol != null) {
            exchange.setProperty("UPGRADED_PROTOCOL", upgradedProtocol);
        }

        return exchange;
    }

    @Override
    public void checkUpgradeRequest(Exchange exchange) {

    }

    private void handleConnectRequest(Exchange exc, Connection con) throws IOException, EndOfStreamException {
        if (configuration.getProxy() != null) {
            exc.getRequest().write(con.out, configuration.getMaxRetries() > 1);
            Response response = fromStream(con.in, false);
            log.debug("Status code response? on CONNECT request: {}", response.getStatusCode());
        }
        exc.getRequest().setUri(NOT_APPLICABLE);
    }


    @Override
    public String checkUpgradeResponse(Exchange exchange, Response response) {
        if (isUpgradeToResponse(response, "websocket") &&
            exchange.getProperty(Exchange.ALLOW_WEBSOCKET) == Boolean.TRUE) {
            return "WebSocket";
        }
        if (isUpgradeToResponse(response, "tcp") &&
            exchange.getProperty(Exchange.ALLOW_TCP) == Boolean.TRUE) {
            return "TCP";
        }
        return null;
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
