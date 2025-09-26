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

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.config.security.*;
import com.predic8.membrane.core.config.spring.*;
import org.springframework.beans.*;
import org.springframework.context.*;

import java.security.*;
import java.util.*;

/**
 * @description Configuration container for Membrane's HTTP client.
 * Allows defining proxy, connection, authentication, TLS, and retry behavior.
 * Can be used as a reusable bean and referenced via &lt;spring:bean&gt;.
 * Most of its sub-elements are optional.
 * <p>
 * <p>YAML:</p>
 * <pre><code>httpClientConfig:
 *   maxRetries: 5
 *   adjustHostHeader: true
 *   connection:
 *     timeout: 10000
 *   proxy:
 *     host: proxy.example.com
 *     port: 3128
 *   authentication:
 *     type: basic
 *     user: user
 *     password: pass
 *   ssl:
 *     keystoreLocation: classpath:client.jks
 *     keystorePassword: secret
 * </code></pre>
 * @topic 4. Transports and Clients
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
     * @description Connection-related configuration such as timeouts and connection pooling.
     * Cannot be null.
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
     * @description Optional proxy configuration for outbound connections.
     */
    @MCChildElement(order = 2)
    public void setProxy(ProxyConfiguration proxy) {
        this.proxy = proxy;
    }

    public AuthenticationConfiguration getAuthentication() {
        return authentication;
    }

    /**
     * @description Optional authentication mechanism (e.g., basic auth).
     */
    @MCChildElement(order = 3)
    public void setAuthentication(AuthenticationConfiguration authentication) {
        this.authentication = authentication;
    }

    public int getMaxRetries() {
        return retryHandler.getRetries();
    }

    /**
     * @description Total number of connection attempts before giving up.
     * This includes the first attempt, so 5 means 1 try + 4 retries.
     * Used for failover and load balancing logic.
     * @default 2
     * @example 3
     */
    @MCAttribute
    public void setMaxRetries(int maxRetries) {
        this.retryHandler.setRetries(maxRetries);
    }

    public SSLParser getSslParser() {
        return sslParser;
    }

    /**
     * @description SSL/TLS configuration for secure connections.
     * Accepts both standard and external SSL configurations.
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
     * @description Enables experimental support for HTTP/2.
     * When true, HTTP/2 connections are attempted when possible.
     * @default false
     */
    @MCAttribute
    public void setUseExperimentalHttp2(boolean useExperimentalHttp2) {
        this.useExperimentalHttp2 = useExperimentalHttp2;
    }

    public RetryHandler getRetryHandler() {
        return retryHandler;
    }

    /**
     * @description Advanced configuration for retry behavior.
     * Allows detailed retry logic beyond the simple maxRetries setting.
     */
    @MCChildElement
    public void setRetryHandler(RetryHandler retryHandler) {
        this.retryHandler = retryHandler;
    }

    public boolean isAdjustHostHeader() {
        return adjustHostHeader;
    }

    /**
     * @description Whether to automatically rewrite the Host header to match the target address.
     * This is useful when routing requests to internal systems where the Host header must match the backend.
     * @default true
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