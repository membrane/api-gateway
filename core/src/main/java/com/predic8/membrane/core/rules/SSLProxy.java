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

package com.predic8.membrane.core.rules;

import com.google.common.base.Objects;
import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.config.security.SSLParser;
import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.sslinterceptor.SSLInterceptor;
import com.predic8.membrane.core.stats.RuleStatisticCollector;
import com.predic8.membrane.core.transport.http.Connection;
import com.predic8.membrane.core.transport.http.ConnectionManager;
import com.predic8.membrane.core.transport.http.StreamPump;
import com.predic8.membrane.core.transport.http.client.ConnectionConfiguration;
import com.predic8.membrane.core.transport.ssl.SSLContext;
import com.predic8.membrane.core.transport.ssl.SSLExchange;
import com.predic8.membrane.core.transport.ssl.SSLProvider;
import com.predic8.membrane.core.transport.ssl.StaticSSLContext;
import com.predic8.membrane.core.util.DNSCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Required;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;

import static com.predic8.membrane.core.interceptor.InterceptorFlowController.ABORTION_REASON;

@MCElement(name="sslProxy")
public class SSLProxy implements Rule {
    private static Logger log = LoggerFactory.getLogger(SSLProxy.class.getName());

    private Target target;
    private ConnectionConfiguration connectionConfiguration = new ConnectionConfiguration();
    private RuleStatisticCollector ruleStatisticCollector = new RuleStatisticCollector();
    private boolean useAsDefault = true;
    private List<SSLInterceptor> sslInterceptors = new ArrayList<>();

    @MCElement(id = "sslProxy-target", name="target", topLevel = false)
    public static class Target {
        private int port = -1;
        private String host;

        public int getPort() {
            return port;
        }

        @MCAttribute
        public void setPort(int port) {
            this.port = port;
        }

        public String getHost() {
            return host;
        }

        @MCAttribute
        public void setHost(String host) {
            this.host = host;
        }
    }

    public ConnectionConfiguration getConnectionConfiguration() {
        return connectionConfiguration;
    }

    @MCChildElement(order = 0)
    public void setConnectionConfiguration(ConnectionConfiguration connectionConfiguration) {
        this.connectionConfiguration = connectionConfiguration;
    }

    public Target getTarget() {
        return target;
    }

    @Required
    @MCChildElement(order = 100)
    public void setTarget(Target target) {
        this.target = target;
    }

    @Override
    public List<Interceptor> getInterceptors() {
        return null;
    }

    @Override
    public void setInterceptors(List<Interceptor> interceptors) {

    }

    public List<SSLInterceptor> getSslInterceptors() {
        return sslInterceptors;
    }

    @MCChildElement(allowForeign=true, order=50)
    public void setSslInterceptors(List<SSLInterceptor> sslInterceptors) {
        this.sslInterceptors = sslInterceptors;
    }


    @Override
    public boolean isBlockRequest() {
        return false;
    }

    @Override
    public boolean isBlockResponse() {
        return false;
    }

    int port;

    public int getPort() {
        return port;
    }

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

    @MCAttribute
    public void setHost(String host) {
        this.host = host;
    }

    @Override
    public RuleKey getKey() {
        return new MyRuleKey();
    }

    @Override
    public void setKey(RuleKey ruleKey) {

    }

    @Override
    public void setName(String name) {
    }

    @Override
    public String getName() {
        return "SSL " + getHost() + ":" + getPort();
    }

    @Override
    public void setBlockRequest(boolean blockStatus) {

    }

    @Override
    public void setBlockResponse(boolean blockStatus) {

    }

    @Override
    public RuleStatisticCollector getStatisticCollector() {
        return ruleStatisticCollector;
    }

    ConnectionManager cm;

    @Override
    public SSLContext getSslInboundContext() {
        return new ForwardingStaticSSLContext();
    }

    @Override
    public SSLProvider getSslOutboundContext() {
        return null;
    }

    Router router;

    @Override
    public void init(Router router) throws Exception {
        this.router = router;
        cm = new ConnectionManager(connectionConfiguration.getKeepAliveTimeout(), router.getTimerManager());
        for (SSLInterceptor i : sslInterceptors)
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
        try {
            clone.init(router);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return clone;
    }

    private class MyRuleKey implements RuleKey {
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
        public boolean complexMatch(String hostHeader, String method, String uri, String version, int port, String localIP) {
            return false;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof MyRuleKey))
                return false;
            MyRuleKey other = (MyRuleKey)obj;
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
            super(getSSLParser(), SSLProxy.this.router.getResolverMap(), SSLProxy.this.router.getBaseLocation());
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
                for (SSLInterceptor interceptor : sslInterceptors) {
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
                    ((Throwable) exc.getProperty(ABORTION_REASON)).printStackTrace();
                byte error = exc.getError().getCode();

                byte[] alert_unrecognized_name = { 21 /* alert */, 3, 1 /* TLS 1.0 */, 0, 2 /* length: 2 bytes */,
                        2 /* fatal */, error };

                try {
                    socket.getOutputStream().write(alert_unrecognized_name);
                } finally {
                    socket.close();
                }

                throw new SocketException("not continuing");
            }

            int port = target.getPort();
            if (port == -1)
                port = getPort();

            StreamPump.StreamPumpStats streamPumpStats = router.getStatistics().getStreamPumpStats();
            String protocol = "SSL";

            Connection con = cm.getConnection(target.getHost(), port, connectionConfiguration.getLocalAddr(), null, connectionConfiguration.getTimeout());

            con.out.write(buffer, 0, position);
            con.out.flush();

            String source = socket.getRemoteSocketAddress().toString();
            String dest = con.toString();
            final StreamPump a = new StreamPump(con.in, socket.getOutputStream(), streamPumpStats, protocol + " " + source + " <- " + dest, SSLProxy.this);
            final StreamPump b = new StreamPump(socket.getInputStream(), con.out, streamPumpStats, protocol + " " + source + " -> " + dest, SSLProxy.this);

            socket.setSoTimeout(0);

            String threadName = Thread.currentThread().getName();
            new Thread(a, threadName + " " + protocol + " Backward Thread").start();
            try {
                Thread.currentThread().setName(threadName + " " + protocol + " Onward Thread");
                b.run();
            } finally {
                try {
                    con.close();
                } catch (IOException e) {
                    log.debug("", e);
                }
            }
            throw new SocketException("SSL Forwarding Connection closed.");
        }

        @Override
        public String constructHostNamePattern() {
            return getKey().getHost();
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof ForwardingStaticSSLContext))
                return false;
            ForwardingStaticSSLContext other = (ForwardingStaticSSLContext)obj;
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
