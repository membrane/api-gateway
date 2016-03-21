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
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.transport.http.HttpClient;
import com.predic8.membrane.core.transport.http.client.HttpClientConfiguration;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Required;

public abstract class AuthorizationService {
    protected Log log;

    private HttpClient httpClient;
    protected Router router;

    protected HttpClientConfiguration httpClientConfiguration;
    protected String clientId;
    protected String clientSecret;
    protected String scope;


    public void init(Router router) throws Exception {
        if(clientId == null)
            throw new Exception("No clientId configured. - Cannot work without one");
        if(clientSecret == null)
            throw new Exception("No clientSecret configured. - Cannot work without one");
        log = LogFactory.getLog(this.getClass().getName());

        setHttpClient(getHttpClientConfiguration() == null ? router.getResolverMap()
                .getHTTPSchemaResolver().getHttpClient() : new HttpClient(
                getHttpClientConfiguration()));
        this.router = router;
        init();
    }

    public abstract void init() throws Exception;
    public abstract String getLoginURL(String securityToken, String publicURL, String pathQuery);

    public abstract String getUserInfoEndpoint();

    public abstract String getSubject();

    public abstract String getTokenEndpoint();

    public abstract String getRevocationEndpoint();


    public HttpClientConfiguration getHttpClientConfiguration() {
        return httpClientConfiguration;
    }

    @MCAttribute
    public void setHttpClientConfiguration(HttpClientConfiguration httpClientConfiguration) {
        this.httpClientConfiguration = httpClientConfiguration;
    }

    public String getClientId() {
        return clientId;
    }

    @Required
    @MCAttribute
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    @Required
    @MCAttribute
    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
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
}
