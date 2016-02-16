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

package com.predic8.membrane.core.interceptor.oauth2;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.interceptor.authentication.session.SessionManager.Session;
import com.predic8.membrane.core.transport.http.HttpClient;
import com.predic8.membrane.core.transport.http.client.HttpClientConfiguration;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Required;

public abstract class AuthorizationService {
    protected Log log;

    protected HttpClient httpClient;
    protected Router router;

    protected HttpClientConfiguration httpClientConfiguration;
    protected String clientId;
    protected String clientSecret;


    public void init(Router router){
        log = LogFactory.getLog(this.getClass().getName());

        httpClient = getHttpClientConfiguration() == null ? router.getResolverMap()
                .getHTTPSchemaResolver().getHttpClient() : new HttpClient(
                getHttpClientConfiguration());
        this.router = router;
        init();
    }

    protected abstract void init();
    protected abstract String getLoginURL(String securityToken, String publicURL, String pathQuery);

    protected abstract String getUserInfoEndpoint();

    protected abstract String getUserIDProperty();

    protected abstract String getTokenEndpoint();


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
}
