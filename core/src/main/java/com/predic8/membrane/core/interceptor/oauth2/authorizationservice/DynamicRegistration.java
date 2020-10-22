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

import com.fasterxml.jackson.core.JsonGenerator;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.config.security.SSLParser;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.MimeType;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.interceptor.InterceptorFlowController;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.oauth2.Client;
import com.predic8.membrane.core.interceptor.oauth2.ReusableJsonGenerator;
import com.predic8.membrane.core.transport.http.AbortException;
import com.predic8.membrane.core.transport.http.HttpClient;
import com.predic8.membrane.core.transport.ssl.SSLContext;
import com.predic8.membrane.core.transport.ssl.StaticSSLContext;
import com.predic8.membrane.core.util.Util;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@MCElement(name="registration")
public class DynamicRegistration {

    ReusableJsonGenerator jsonGenerator = new ReusableJsonGenerator();

    private List<Interceptor> interceptors = new ArrayList<Interceptor>();
    private SSLParser sslParser;
    private SSLContext sslContext;
    private InterceptorFlowController flowController = new InterceptorFlowController();
    private HttpClient client = new HttpClient();

    public void init(Router router) throws Exception {
        if (sslParser != null)
            sslContext = new StaticSSLContext(sslParser, router.getResolverMap(), router.getBaseLocation());
        for(Interceptor i : interceptors)
            i.init(router);
    }

    public InputStream retrieveOpenIDConfiguration(String uri) throws Exception {
        return doRequest(new Request.Builder().get(uri).buildExchange()).getBodyAsStream();
    }

    public Client registerWithCallbackAt(List<String> callbackUris, String registrationEndpoint) throws Exception {
        Exchange exc = new Request.Builder()
                .post(registrationEndpoint)
                .header(Header.CONTENT_TYPE, MimeType.APPLICATION_JSON_UTF8)
                .body(getRegistrationBody(callbackUris))
                .buildExchange();

        Response response = doRequest(exc);

        HashMap<String, String> json = Util.parseSimpleJSONResponse(response);

        if(!json.containsKey("client_id") || !json.containsKey("client_secret"))
            throw new RuntimeException("Registration endpoint didn't return clientId/clientSecret");

        return new Client(json.get("client_id"),json.get("client_secret"),"");
    }

    private Response doRequest(Exchange exc) throws Exception {
        if (sslContext != null)
            exc.setProperty(Exchange.SSL_CONTEXT, sslContext);

        if(flowController.invokeRequestHandlers(exc,interceptors) != Outcome.CONTINUE)
            throw new RuntimeException("Registration interceptorchain (request) had a problem");

        Response response = client.call(exc).getResponse();

        try{
            flowController.invokeResponseHandlers(exc);
        } catch (AbortException e){
            throw new RuntimeException("Registration interceptorchain (response) had a problem");
        }

        if(response.getStatusCode() < 200 || response.getStatusCode() > 201)
            throw new RuntimeException("Registration endpoint didn't return successful: " + response.getStatusMessage());
        return response;
    }

    private String getRegistrationBody(List<String> callbackUris) throws IOException {
        JsonGenerator jsonGen = jsonGenerator.resetAndGet();
        jsonGen.writeStartObject();
        jsonGen.writeArrayFieldStart("redirect_uris");
        for (String callbackUri : callbackUris)
            jsonGen.writeString(callbackUri);
        jsonGen.writeEndArray();
        jsonGen.writeEndObject();
        return jsonGenerator.getJson();
    }

    public SSLParser getSslParser() {
        return sslParser;
    }

    @MCChildElement(order = 10, allowForeign = true)
    public void setSslParser(SSLParser sslParser) {
        this.sslParser = sslParser;
    }

    public List<Interceptor> getInterceptors() {
        return interceptors;
    }

    @MCChildElement(order = 20)
    public void setInterceptors(List<Interceptor> interceptors) {
        this.interceptors = interceptors;
    }
}
