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

package com.predic8.membrane.core.interceptor.oauth2.tokenvalidation;

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.transport.http.*;

import java.net.*;

import static com.predic8.membrane.core.interceptor.Outcome.*;

@MCElement(name="tokenValidator")
public class OAuth2TokenValidatorInterceptor extends AbstractInterceptor {

    private String endpoint;

    private HttpClient client;

    @Override
    public void init() {
        super.init();
        setFlow(Flow.Set.REQUEST_FLOW);
        name = "OAuth2 Token Validator";
        client = router.getHttpClientFactory().createClient(null);
    }

    @Override
    public String getShortDescription() {
        return "The token validator grants access to resources with valid access tokens. <br/>" +
                "Endpoint: " + endpoint;
    }

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        synchronized (client){
            if(callExchangeAndCheckFor200(buildAccessTokenValidationExchange(exc)))
                return CONTINUE;
        }
        setResponseToBadRequest(exc);
        return Outcome.RETURN;
    }

    private boolean callExchangeAndCheckFor200(Exchange e) throws Exception {
        return client.call(e).getResponse().getStatusCode() == 200;
    }

    private void setResponseToBadRequest(Exchange exc) {
        new Response();
        exc.setResponse(Response.badRequest().build());
    }

    private Exchange buildAccessTokenValidationExchange(Exchange exc) throws URISyntaxException {
        return new Request.Builder().get(endpoint).header(Header.AUTHORIZATION, getAuthorizationHeaderValue(exc)).buildExchange();
    }

    private String getAuthorizationHeaderValue(Exchange exc) {
        return exc.getRequest().getHeader().getFirstValue(Header.AUTHORIZATION);
    }

    public String getEndpoint() {
        return endpoint;
    }

    /**
     *
     * @description the endpoint that validates the access token
     */
    @Required
    @MCAttribute
    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }
}
