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

package com.predic8.membrane.core.transport.http;

import com.predic8.membrane.core.config.security.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.resolver.*;
import com.predic8.membrane.core.transport.http.client.*;
import com.predic8.membrane.core.transport.http2.*;
import com.predic8.membrane.core.transport.ssl.*;
import com.predic8.membrane.core.util.*;
import org.jetbrains.annotations.*;

import javax.annotation.concurrent.*;
import java.io.*;

import static com.predic8.membrane.core.exchange.Exchange.*;

public class ConnectionFactory {

    private static final String[] HTTP2_PROTOCOLS = new String[]{"h2"};
    private static final String[] HTTP1_PROTOCOLS = new String[]{};

    private final ConnectionManager connectionManager;
    private final Http2ClientPool http2ClientPool;
    private final HttpClientConfiguration config;
    private final SSLContext proxySSLContext;
    private final SSLContext sslContext;

    @GuardedBy("ConnectionFactory.class")
    private static SSLProvider defaultSSLProvider;

    public ConnectionFactory(HttpClientConfiguration config, TimerManager timerManager) {
        this.config = config;
        this.http2ClientPool = getHttp2ClientPool(config);
        this.proxySSLContext = getProxySSLContext(config.getProxy());
        this.sslContext = getSSLContext(config);
        connectionManager = new ConnectionManager(config.getConnection().getKeepAliveTimeout(), timerManager);
    }

    public OutgoingConnectionType getConnection(Exchange exc, HostColonPort target, int attempts) throws IOException {

        Connection con = getExchangeAttachedConnection(exc, attempts, target);

        boolean usingHttp2 = false;

        SSLProvider sslProvider = getOutboundSSLProvider(exc, target);
        Http2Client h2c = null;
        String sniServerName = exc.getProperty(SNI_SERVER_NAME, String.class);

        if (con == null && config.isUseExperimentalHttp2()) {
            h2c = http2ClientPool.reserveStream(target.host(), target.port(), sslProvider, sniServerName, config.getProxy(), proxySSLContext);
            if (h2c != null) {
                con = h2c.getConnection();
                usingHttp2 = true;
            }
        }

        if (con == null) {
            con = connectionManager.getConnection(target.host(), target.port(), config.getConnection().getLocalAddr(), sslProvider, config.getConnection().getTimeout(),
                    sniServerName, config.getProxy(), proxySSLContext, getApplicationProtocols());
            if (config.isUseExperimentalHttp2() && Http2TlsSupport.isHttp2(con.socket))
                usingHttp2 = true;
            else
                exc.setTargetConnection(con); // TODO Question: HTTP2 Connection is never set to exchange
            con.setKeepAttachedToExchange(usingHttp2 || exc.getRequest().isBindTargetConnectionToIncoming()); // e.g. for NTML
        }
        return new OutgoingConnectionType(con, usingHttp2, sslProvider, h2c, sniServerName);
    }

    /**
     * E.g. for protocols like NTLM, an outbound TCP connection gets bound to an inbound TCP connection. This
     * is realized by binding the TCP connection to the Exchange here, and binding it to the next Exchange in
     * {@link HttpServerHandler}.
     */
    private static @org.jetbrains.annotations.Nullable Connection getExchangeAttachedConnection(Exchange exc, int counter, HostColonPort target) throws IOException {
        Connection con = null;
        if (counter == 0) {
            con = exc.getTargetConnection();
            if (con != null) {
                if (!con.isSame(target.host(), target.port())) {
                    con.close();
                    con = null;
                } else {
                    con.setKeepAttachedToExchange(true);
                }
            }
        }
        return con;
    }

    private SSLProvider getOutboundSSLProvider(Exchange exc, HostColonPort hcp) {
        SSLProvider sslPropObj = exc.getProperty(SSL_CONTEXT, SSLProvider.class);
        if (sslPropObj != null)
            return sslPropObj;
        if (hcp.useSSL())
            return sslContext != null ? sslContext : getDefaultSSLProvider();
        return null;
    }

    private static synchronized SSLProvider getDefaultSSLProvider() {
        if (defaultSSLProvider == null)
            defaultSSLProvider = new StaticSSLContext(new SSLParser(), null, null);
        return defaultSSLProvider;
    }

    private static @org.jetbrains.annotations.Nullable SSLContext getProxySSLContext(ProxyConfiguration proxy) {
        if (proxy != null && proxy.getSslParser() != null)
            return new StaticSSLContext(proxy.getSslParser(), new ResolverMap(), null);
        return null;
    }

    private String[] getApplicationProtocols() {
        if (config.isUseExperimentalHttp2()) {
            return HTTP2_PROTOCOLS;
        }
        return HTTP1_PROTOCOLS;
    }

    private static @org.jetbrains.annotations.Nullable SSLContext getSSLContext(@NotNull HttpClientConfiguration configuration) {
        if (configuration.getSslParser() == null)
            return null;
        if (configuration.getBaseLocation() == null)
            throw new RuntimeException("Cannot find keystores as base location is unknown");
        return new StaticSSLContext(configuration.getSslParser(), new ResolverMap(), configuration.getBaseLocation());

    }

    private @org.jetbrains.annotations.Nullable Http2ClientPool getHttp2ClientPool(@NotNull HttpClientConfiguration configuration) {
        if (configuration.isUseExperimentalHttp2())
            return new Http2ClientPool(configuration.getConnection().getKeepAliveTimeout());
        return null;
    }

    public SSLContext getProxySSLContext() {
        return proxySSLContext;
    }

    public Http2ClientPool getHttp2ClientPool() {
        return http2ClientPool;
    }

    public ConnectionManager getConnectionManager() {
        return connectionManager;
    }

    public record OutgoingConnectionType(Connection con, boolean usingHttp2, SSLProvider sslProvider, Http2Client h2c,
                                         String sniServerName) {
    }
}
