/*
 * Copyright 2016 predic8 GmbH, www.predic8.com
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.predic8.membrane.core.interceptor.oauth2.authorizationservice;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.config.security.SSLParser;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.oauth2.Client;
import com.predic8.membrane.core.transport.http.HttpClient;
import com.predic8.membrane.core.transport.http.client.HttpClientConfiguration;
import com.predic8.membrane.core.transport.ssl.SSLContext;
import com.predic8.membrane.core.transport.ssl.StaticSSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.concurrent.GuardedBy;
import java.util.List;

public abstract class AuthorizationService {
    protected Logger log;

    private HttpClient httpClient;
    protected Router router;

    protected HttpClientConfiguration httpClientConfiguration;
    private final Object lock = new Object();
    @GuardedBy("lock")
    private String clientId;
    @GuardedBy("lock")
    private String clientSecret;
    protected String scope;
    private SSLParser sslParser;
    private SSLContext sslContext;

    protected boolean supportsDynamicRegistration = false;

    public boolean supportsDynamicRegistration(){
        return supportsDynamicRegistration;
    }


    public void init(Router router) throws Exception {
        log = LoggerFactory.getLogger(this.getClass().getName());

        setHttpClient(getHttpClientConfiguration() == null ? router.getResolverMap()
                .getHTTPSchemaResolver().getHttpClient(router.getTimerManager()) : new HttpClient(
                getHttpClientConfiguration(), router.getTimerManager()));
        if (sslParser != null)
            sslContext = new StaticSSLContext(sslParser, router.getResolverMap(), router.getBaseLocation());
        this.router = router;
        init();
        if(!supportsDynamicRegistration())
            checkForClientIdAndSecret();
    }

    public abstract void init() throws Exception;

    public abstract String getIssuer();

    public abstract String getJwksEndpoint() throws Exception;

    public abstract String getLoginURL(String securityToken, String callbackURL, String callbackPath);

    public abstract String getUserInfoEndpoint();

    public abstract String getSubject();

    public abstract String getTokenEndpoint();

    public abstract String getRevocationEndpoint();

    protected void doDynamicRegistration(List<String> callbackURLs) throws Exception {
    }

    public void dynamicRegistration(List<String> callbackURLs) throws Exception {
        if(supportsDynamicRegistration())
            doDynamicRegistration(callbackURLs);
    }

    protected void checkForClientIdAndSecret(){
        synchronized (lock) {
            if (clientId == null || clientSecret == null)
                throw new RuntimeException(this.getClass().getSimpleName() + " cannot work without specified clientId and clientSecret");
        }
    }


    public HttpClientConfiguration getHttpClientConfiguration() {
        return httpClientConfiguration;
    }

    @MCAttribute
    public void setHttpClientConfiguration(HttpClientConfiguration httpClientConfiguration) {
        this.httpClientConfiguration = httpClientConfiguration;
    }

    public String getClientId() {
        synchronized (lock) {
            return clientId;
        }
    }

    @MCAttribute
    public void setClientId(String clientId) {
        synchronized (lock) {
            this.clientId = clientId;
        }
    }

    public String getClientSecret() {
        synchronized (lock) {
            return clientSecret;
        }
    }

    @MCAttribute
    public void setClientSecret(String clientSecret) {
        synchronized (lock) {
            this.clientSecret = clientSecret;
        }
    }

    protected void setClientIdAndSecret(String clientId, String clientSecret) {
        synchronized (lock) {
            this.clientId = clientId;
            this.clientSecret = clientSecret;
        }
    }


    public String getScope() {
        return scope;
    }

    @MCAttribute
    public void setScope(String scope) {
        this.scope = scope;
    }

    public HttpClient getHttpClient() {
        return httpClient;
    }

    public void setHttpClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public Response doRequest(Exchange e) throws Exception {
        if (sslContext != null)
            e.setProperty(Exchange.SSL_CONTEXT, sslContext);
        return getHttpClient().call(e).getResponse();
    }

    public SSLParser getSslParser() {
        return sslParser;
    }

    @MCChildElement(order=20, allowForeign = true)
    public void setSslParser(SSLParser sslParser) {
        this.sslParser = sslParser;
    }
}
