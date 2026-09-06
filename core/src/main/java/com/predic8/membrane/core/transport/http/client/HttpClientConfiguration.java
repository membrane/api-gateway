/* Copyright 2013 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.transport.http.client;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.config.security.SSLParser;
import com.predic8.membrane.core.config.spring.BaseLocationApplicationContext;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.security.InvalidParameterException;
import java.util.Objects;

/**
 * @description Settings the HTTP client uses for calls to a backend: socket timeouts, an upstream
 *              proxy, credentials, TLS and retry behavior. Every part is optional and falls back to
 *              its defaults when omitted. Configure it once globally, or on a single plugin that
 *              calls a backend.
 * @topic 7. Transports and Clients
 * @yaml <pre><code>
 * configuration:
 *   httpClientConfig:
 *     connection:
 *       timeout: 5000
 *     proxy:
 *       host: proxy.example.com
 *       port: 3128
 *     retries:
 *       retries: 3
 * </code></pre>
 */
@MCElement(name = "httpClientConfig")
public class HttpClientConfiguration implements ApplicationContextAware {

    /**
     * Settings for low-level connection behavior such as timeouts and pooling.
     */
    private ConnectionConfiguration connection = new ConnectionConfiguration();

    /**
     * Optional proxy server configuration.
     */
    private ProxyConfiguration proxy;

    /**
     * Optional authentication configuration (e.g. basic auth).
     */
    private AuthenticationConfiguration authentication;

    /**
     * Optional TLS/SSL configuration for secure communication.
     */
    private SSLParser sslParser;

    /**
     * Optional base location for resolving relative paths, e.g. to certificates.
     * Set automatically by Spring when using BaseLocationApplicationContext.
     */
    private String baseLocation;

    /**
     * Whether the Host header should be rewritten to match the target host.
     * Default: true
     */
    private boolean adjustHostHeader = true;

    /**
     * Enables experimental HTTP/2 support if true.
     */
    private boolean useExperimentalHttp2;

    private RetryHandler retryHandler = new RetryHandler();

    public HttpClientConfiguration() {
    }

    public ConnectionConfiguration getConnection() {
        return connection;
    }

    /**
     * @description Socket timeouts and the local network interface used for outgoing connections.
     */
    @MCChildElement(order = 1)
    public void setConnection(ConnectionConfiguration connection) {
        if (connection == null)
            throw new InvalidParameterException("'connection' parameter cannot be null.");
        this.connection = connection;
    }

    public ProxyConfiguration getProxy() {
        return proxy;
    }

    /**
     * @description Upstream HTTP proxy that outgoing requests are routed through.
     */
    @MCChildElement(order = 2)
    public void setProxy(ProxyConfiguration proxy) {
        this.proxy = proxy;
    }

    public AuthenticationConfiguration getAuthentication() {
        return authentication;
    }

    /**
     * @description Credentials sent as HTTP Basic Authentication with every outgoing request.
     */
    @MCChildElement(order = 3)
    public void setAuthentication(AuthenticationConfiguration authentication) {
        this.authentication = authentication;
    }

    public SSLParser getSslParser() {
        return sslParser;
    }

    /**
     * @description TLS settings for outgoing HTTPS connections, such as the trust store that validates
     * the backend certificate and a key store holding a client certificate.
     */
    @MCChildElement(order = 4, allowForeign = true)
    public void setSslParser(SSLParser sslParser) {
        this.sslParser = sslParser;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        if (applicationContext instanceof BaseLocationApplicationContext)
            setBaseLocation(((BaseLocationApplicationContext) applicationContext).getBaseLocation());
    }

    public String getBaseLocation() {
        return baseLocation;
    }

    public void setBaseLocation(String baseLocation) {
        this.baseLocation = baseLocation;
    }

    public boolean isUseExperimentalHttp2() {
        return useExperimentalHttp2;
    }

    /**
     * @description Negotiates HTTP/2 over TLS when the backend offers it and falls back to HTTP/1.1
     * otherwise. Experimental.
     * @default false
     * @example true
     */
    @MCAttribute
    public void setUseExperimentalHttp2(boolean useExperimentalHttp2) {
        this.useExperimentalHttp2 = useExperimentalHttp2;
    }

    public RetryHandler getRetryHandler() {
        return retryHandler;
    }

    /**
     * @description How often and how quickly a failed call to the backend is repeated.
     */
    @MCChildElement
    public void setRetryHandler(RetryHandler retryHandler) {
        this.retryHandler = retryHandler;
    }

    public boolean isAdjustHostHeader() {
        return adjustHostHeader;
    }

    /**
     * @description Rewrites the Host header to the address of the backend. Set to <code>false</code> to
     * forward the Host header the client sent, e.g. when the backend serves virtual hosts under that name.
     * @default true
     * @example false
     */
    @MCAttribute
    public void setAdjustHostHeader(boolean adjustHostHeader) {
        this.adjustHostHeader = adjustHostHeader;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HttpClientConfiguration that = (HttpClientConfiguration) o;
        return Objects.equals(retryHandler, that.getRetryHandler())
                && useExperimentalHttp2 == that.useExperimentalHttp2
                && adjustHostHeader == that.adjustHostHeader
                && Objects.equals(connection, that.connection)
                && Objects.equals(proxy, that.proxy)
                && Objects.equals(authentication, that.authentication)
                && Objects.equals(sslParser, that.sslParser)
                && Objects.equals(baseLocation, that.baseLocation);
    }

    @Override
    public int hashCode() {
        return Objects.hash(retryHandler,
                connection,
                proxy,
                authentication,
                sslParser,
                adjustHostHeader,
                baseLocation,
                useExperimentalHttp2);
    }
}