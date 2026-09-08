/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.tutorials.ssl_tls;

import com.predic8.membrane.examples.util.SubstringWaitableConsoleEvent;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Verifies tutorial step 30-TLS-Passthrough.yaml: two sslProxies share port 8443 and the
 * TLS server name (SNI) decides which backend a connection reaches. RestAssured cannot be
 * used here - it resolves the hostname via DNS and offers no equivalent of curl's --resolve,
 * while the whole point is to send a server name that does not resolve to the gateway. So the
 * TLS connection is opened by hand with an explicit SNI, against the default trust store:
 * a successful handshake means the backend's own certificate reached the client, which is the
 * programmatic form of the 'curl -v' certificate output the tutorial shows.
 */
public class TlsPassthroughTutorialTest extends AbstractSslTlsTutorialTest {

    private static final int GATEWAY_PORT = 8443;

    @Override
    protected String getTutorialYaml() {
        return "30-TLS-Passthrough.yaml";
    }

    @Test
    void routesToShopApiBySni() throws Exception {
        assumeInternet("api.predic8.de");
        var logged = new SubstringWaitableConsoleEvent(process,
                "api.predic8.de:8443: TLS connection from");

        Response response = get("api.predic8.de", "/shop/v2/products");

        assertTrue(response.statusLine.contains("200"), response.statusLine);
        assertTrue(response.peerPrincipal.contains("api.predic8.de"), response.peerPrincipal);
        assertTrue(response.body.contains("\"products\""), response.body);
        logged.waitFor(5000); // sslLog must report the forwarded connection
    }

    @Test
    void routesToWebsiteBySni() throws Exception {
        assumeInternet("www.membrane-api.io");
        var logged = new SubstringWaitableConsoleEvent(process,
                "www.membrane-api.io:8443: TLS connection from");

        Response response = get("www.membrane-api.io", "/robots.txt");

        assertTrue(response.statusLine.contains("200"), response.statusLine);
        assertTrue(response.peerPrincipal.contains("www.membrane-api.io"), response.peerPrincipal);
        assertTrue(response.body.contains("Sitemap:"), response.body);
        logged.waitFor(5000); // sslLog must report the forwarded connection
    }

    @Test
    void rejectsConnectionWithUnknownServerName() {
        var e = assertThrows(SSLHandshakeException.class, () -> get("localhost", "/"));

        assertTrue(e.getMessage().contains("unrecognized_name"), e.getMessage());
    }

    private record Response(String statusLine, String body, String peerPrincipal) {
    }

    /**
     * Sends one request through the gateway with {@code serverName} as the TLS server name,
     * no matter what that name resolves to - the equivalent of curl's --resolve. Trust comes
     * from the default trust store, so the handshake only succeeds if the backend's own
     * certificate reached us.
     * <p>
     * A fresh SSLContext per call is essential: JSSE caches client sessions per host and port,
     * and a resumed session re-sends the server name it was created with - every call after the
     * first would silently be routed to the proxy the first one matched.
     */
    private Response get(String serverName, String path) throws Exception {
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, null, null);
        try (SSLSocket socket = (SSLSocket) context.getSocketFactory()
                .createSocket(InetAddress.getLoopbackAddress(), GATEWAY_PORT)) {
            SSLParameters parameters = socket.getSSLParameters();
            parameters.setServerNames(List.of(new SNIHostName(serverName)));
            socket.setSSLParameters(parameters);
            socket.setSoTimeout(10000);
            socket.startHandshake();

            OutputStream out = socket.getOutputStream();
            out.write("""
                    GET %s HTTP/1.1\r
                    Host: %s\r
                    Connection: close\r
                    \r
                    """.formatted(path, serverName).getBytes(UTF_8));
            out.flush();

            var in = new BufferedReader(new InputStreamReader(socket.getInputStream(), UTF_8));
            return new Response(in.readLine(), String.join("\n", in.lines().toList()),
                    socket.getSession().getPeerPrincipal().getName());
        }
    }

    private static void assumeInternet(String host) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, 443), 3000);
        } catch (Exception e) {
            assumeTrue(false, host + " is not reachable - skipping passthrough test");
        }
    }
}
