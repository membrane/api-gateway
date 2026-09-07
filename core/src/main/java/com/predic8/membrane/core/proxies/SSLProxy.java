/* Copyright 2016 predic8 GmbH, www.predic8.com

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License. */

package com.predic8.membrane.core.proxies;

import com.google.common.base.Objects;
import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.annot.Required;
import com.predic8.membrane.core.config.security.SSLParser;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.router.Router;
import com.predic8.membrane.core.sslinterceptor.SSLInterceptor;
import com.predic8.membrane.core.stats.RuleStatisticCollector;
import com.predic8.membrane.core.transport.http.Connection;
import com.predic8.membrane.core.transport.http.ConnectionManager;
import com.predic8.membrane.core.transport.http.client.ConnectionConfiguration;
import com.predic8.membrane.core.transport.http.streampump.StreamPump;
import com.predic8.membrane.core.transport.ssl.*;
import com.predic8.membrane.core.util.DNSCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;

import static com.predic8.membrane.core.interceptor.FlowController.ABORTION_REASON;
import static com.predic8.membrane.core.util.BeanDefinitionBasePathUtil.resolveBaseLocation;

/**
 * @description Forwards TLS connections to a backend without terminating them. Membrane does not
 * decrypt the traffic and can therefore neither inspect nor modify it: the TLS session ends at the
 * backend and the client sees the backend's certificate. Use it in front of services that have to
 * keep an end-to-end encrypted connection to their clients.
 * <p>Several sslProxy elements can share one port. The server name the client sends in the TLS SNI
 * extension selects the one that handles a connection, so every backend needs its own hostname.
 * Clients that send no server name reach the first proxy on that port with
 * <code>useAsDefault</code> enabled.</p>
 * <p>See tutorials/ssl-tls/30-TLS-Passthrough.yaml.</p>
 * @topic 1. Proxies and Flow
 * @yaml
 * <pre><code>
 * sslProxy:
 *   host: api.example.com
 *   port: 8443
 *   target:
 *     host: api.example.com
 *     port: 443
 * </code></pre>
 */
@MCElement(name = "sslProxy", topLevel = true, component = false)
public class SSLProxy implements Proxy {
    private static final Logger log = LoggerFactory.getLogger(SSLProxy.class.getName());

    private SSLProxy.Target target;
    private ConnectionConfiguration connectionConfiguration = new ConnectionConfiguration();
    private final RuleStatisticCollector ruleStatisticCollector = new RuleStatisticCollector();
    private boolean useAsDefault = true;
    private List<SSLInterceptor> interceptors = new ArrayList<>();

    public ConnectionConfiguration getConnectionConfiguration() {
        return connectionConfiguration;
    }

    /**
     * @description Timeouts, keep-alive and local address of the connections opened to the target.
     */
    @MCChildElement(order = 0)
    public void setConnectionConfiguration(ConnectionConfiguration connectionConfiguration) {
        this.connectionConfiguration = connectionConfiguration;
    }

    /**
     * @description Address of the backend an sslProxy forwards to.
     */
    @MCElement(id = "sslProxy-target", name = "target", component = false)
    public static class Target {
        private int port = -1;
        private String host;

        public int getPort() {
            return port;
        }

        /**
         * @description Port on the backend the connection is forwarded to.
         * @default the port the sslProxy listens on
         * @example 443
         */
        @MCAttribute
        public void setPort(int port) {
            this.port = port;
        }

        public String getHost() {
            return host;
        }

        /**
         * @description Hostname or IP address of the backend.
         * @example api.example.com
         */
        @MCAttribute
        public void setHost(String host) {
            this.host = host;
        }
    }

    public SSLProxy.Target getTarget() {
        return target;
    }

    /**
     * @description Backend the encrypted connection is forwarded to.
     */
    @Required
    @MCChildElement(order = 100)
    public void setTarget(SSLProxy.Target target) {
        this.target = target;
    }

    @Override
    public List<Interceptor> getFlow() {
        return null;
    }

    @Override
    public void setFlow(List<Interceptor> flow) {

    }

    public List<SSLInterceptor> getInterceptors() {
        return interceptors;
    }

    /**
     * @description Plugins that inspect a connection before it is forwarded and can reject it.
     *              They only see the data of the TLS handshake, never the encrypted payload.
     */
    @MCChildElement(allowForeign = true, order = 50)
    public void setInterceptors(List<SSLInterceptor> interceptors) {
        this.interceptors = interceptors;
    }

    int port;

    public int getPort() {
        return port;
    }

    /**
     * @description Port the gateway accepts TLS connections on.
     * @example 8443
     */
    @MCAttribute
    public void setPort(int port) {
        this.port = port;
    }

    String ip;

    public String getIp() {
        return ip;
    }

    /**
     * @description If present, binds the port only on the specified IP. Useful for hosts with multiple IP addresses.
     * @default <i>not set</i>
     * @example 127.0.0.1
     */
    @MCAttribute
    public void setIp(String ip) {
        this.ip = ip;
    }

    String host;

    public String getHost() {
        return host;
    }

    /**
     * @description Restricts this proxy to connections whose TLS server name (SNI) matches one of
     *              the given hostnames. Separate multiple hostnames with spaces. The asterisk
     *              <code>*</code> matches any number of characters, including zero, for basic globbing.
     * @example api.example.com
     */
    @Required
    @MCAttribute
    public void setHost(String host) {
        this.host = host;
    }

    @Override
    public RuleKey getKey() {
        return new SSLProxyKey();
    }

    @Override
    public void setKey(RuleKey ruleKey) {

    }

    @Override
    public void setName(String name) {
    }

    @Override
    public String getName() {
        return "SSL %s:%d".formatted(getHost(), getPort());
    }

    @Override
    public RuleStatisticCollector getStatisticCollector() {
        return ruleStatisticCollector;
    }

    ConnectionManager cm;

    public SSLContext getSslInboundContext() {
        return new ForwardingStaticSSLContext();
    }


    public SSLProvider getSslOutboundContext() {
        return null;
    }

    Router router;

    @Override
    public void init(Router router) {
        this.router = router;
        cm = new ConnectionManager(connectionConfiguration.getKeepAliveTimeout(), router.getTimerManager());
        for (SSLInterceptor i : interceptors)
            i.init(router);
    }

    @Override
    public boolean isTargetAdjustHostHeader() {
        return false;
    }

    @Override
    public boolean isActive() {
        return true;
    }

    @Override
    public String getErrorState() {
        return null;
    }

    @Override
    public SSLProxy clone() throws CloneNotSupportedException {
        SSLProxy clone = (SSLProxy) super.clone();
        clone.init(router);
        return clone;
    }

    @Override
    public String getProtocol() {
        return "";
    }

    /**
     * The port connections are forwarded to: the one configured on the target, or the port this
     * proxy listens on when the target does not name one.
     */
    public int getTargetPort() {
        int targetPort = target.getPort();
        return targetPort != -1 ? targetPort : getPort();
    }

    private String getBeanBaseLocation() {
        return resolveBaseLocation(this, router);
    }

    /**
     * An {@link SSLInterceptor} that rejects a connection is expected to set the alert it wants the
     * client to see. If it did not - or if it aborted by throwing - fall back to internal_error
     * instead of failing with a NullPointerException while assembling the alert.
     */
    private static byte alertCode(SSLExchange exc) {
        TLSError error = exc.getError();
        return (error != null ? error : TLSError.internal_error).getCode();
    }

    private class SSLProxyKey implements RuleKey {
        @Override
        public int getPort() {
            return port;
        }

        @Override
        public String getMethod() {
            return null;
        }

        @Override
        public String getPath() {
            return null;
        }

        @Override
        public String getHost() {
            return host;
        }

        @Override
        public boolean isMethodWildcard() {
            return false;
        }

        @Override
        public boolean isPathRegExp() {
            return false;
        }

        @Override
        public boolean isUsePathPattern() {
            return false;
        }

        @Override
        public void setUsePathPattern(boolean usePathPattern) {

        }

        @Override
        public void setPathRegExp(boolean pathRegExp) {

        }

        @Override
        public void setPath(String path) {

        }

        @Override
        public boolean matchesPath(String path) {
            return false;
        }

        @Override
        public String getIp() {
            return ip;
        }

        @Override
        public void setIp(String ip) {

        }

        @Override
        public boolean matchesHostHeader(String hostHeader) {
            return false;
        }

        @Override
        public boolean matchesVersion(String version) {
            return false;
        }

        @Override
        public boolean complexMatch(Exchange exc) {
            return false;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof SSLProxyKey other))
                return false;
            return Objects.equal(getHost(), other.getHost()) && getPort() == other.getPort();
        }
    }

    private SSLParser getSSLParser() {
        SSLParser sslParser = new SSLParser();
        sslParser.setUseAsDefault(useAsDefault);
        return sslParser;
    }

    private class ForwardingStaticSSLContext extends StaticSSLContext {

        public ForwardingStaticSSLContext() {
            super(getSSLParser(), SSLProxy.this.router.getResolverMap(), SSLProxy.this.getBeanBaseLocation());
        }

        @Override
        public Socket wrap(Socket socket, byte[] buffer, int position) throws IOException {
            DNSCache dnsCache = SSLProxy.this.router.getDnsCache();
            SSLExchange exc = new SSLExchange();
            InetAddress remoteAddr = socket.getInetAddress();
            String ip = dnsCache.getHostAddress(remoteAddr);
            exc.setRemoteAddrIp(ip);
            exc.setRemotePort(socket.getPort());
            exc.setRule(SSLProxy.this);

            boolean cont = true;
            try {
                for (SSLInterceptor interceptor : interceptors) {
                    Outcome o = interceptor.handleRequest(exc);
                    if (o != Outcome.CONTINUE) {
                        cont = false;
                        break;
                    }
                }
            } catch (Exception e) {
                exc.setProperty(ABORTION_REASON, e);
                cont = false;
            }

            if (!cont) {
                if (exc.getProperty(ABORTION_REASON) != null && exc.getProperty(ABORTION_REASON) instanceof Throwable)
                    log.error("", (Throwable) exc.getProperty(ABORTION_REASON));
                byte[] alert = {21 /* alert */, 3, 1 /* TLS 1.0 */, 0, 2 /* length: 2 bytes */,
                        2 /* fatal */, alertCode(exc)};

                try (socket) {
                    socket.getOutputStream().write(alert);
                }

                throw new SocketException("not continuing");
            }

            StreamPump.StreamPumpStats streamPumpStats = router.getStatistics().getStreamPumpStats();
            String protocol = "SSL";

            Connection con = cm.getConnection(target.getHost(), getTargetPort(), connectionConfiguration.getLocalAddr(), null, connectionConfiguration.getTimeout());

            con.out.write(buffer, 0, position);
            con.out.flush();

            String source = socket.getRemoteSocketAddress().toString();
            String dest = con.toString();
            final StreamPump a = new StreamPump(con.in, socket.getOutputStream(), streamPumpStats, protocol + " " + source + " <- " + dest, SSLProxy.this);
            final StreamPump b = new StreamPump(socket.getInputStream(), con.out, streamPumpStats, protocol + " " + source + " -> " + dest, SSLProxy.this);

            socket.setSoTimeout(0);

            StreamPump.runClient(log, a, protocol, b, con);
            throw new SocketException("SSL Forwarding Connection closed.");
        }

        @Override
        public String constructHostNamePattern() {
            return getKey().getHost();
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof ForwardingStaticSSLContext other))
                return false;
            return Objects.equal(SSLProxy.this, other.getSSLProxy());
        }

        public SSLProxy getSSLProxy() {
            return SSLProxy.this;
        }

        @Override
        public String getPrometheusContextTypeName() {
            return "forwarding";
        }

        @Override
        public boolean hasKeyAndCertificate() {
            return false;
        }
    }

    public boolean isUseAsDefault() {
        return useAsDefault;
    }

    /**
     * @description whether to use the SSLContext built from this SSLProxy when no SNI header was transmitted.
     * @default true
     */
    @MCAttribute
    public void setUseAsDefault(boolean useAsDefault) {
        this.useAsDefault = useAsDefault;
    }
}
