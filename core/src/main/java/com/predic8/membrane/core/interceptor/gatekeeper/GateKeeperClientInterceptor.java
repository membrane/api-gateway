/* Copyright 2019 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.gatekeeper;

import com.fasterxml.jackson.databind.*;
import com.google.common.cache.*;
import com.google.common.collect.*;
import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.transport.http.*;
import com.predic8.membrane.core.transport.http.client.*;

import java.util.*;
import java.util.concurrent.*;

import static com.predic8.membrane.core.http.MimeType.*;

@MCElement(name = "gatekeeper")
public class GateKeeperClientInterceptor extends AbstractInterceptor {

    private String url;
    private HttpClientConfiguration httpClientConfiguration;

    private HttpClient httpClient;
    private ObjectMapper om =  new ObjectMapper();

    public GateKeeperClientInterceptor() {
        name = "gatekeeper";
        setFlow(Flow.Set.REQUEST);
    }

    public GateKeeperClientInterceptor(String url) {
        this.url = url;
        name = "gatekeeper";
        setFlow(Flow.Set.REQUEST);
    }

    @Override
    public void init(Router router) throws Exception {
        super.init(router);
        httpClient = router.getHttpClientFactory().createClient(httpClientConfiguration);
    }

    Cache<String, Map> cache = CacheBuilder.newBuilder().expireAfterWrite(1, TimeUnit.MINUTES).build();

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {

        String ruleName = exc.getRule().getName();
        String clientIP = exc.getRemoteAddrIp();


        String body = om.writeValueAsString(ImmutableMap.builder()
                .put("rule", ruleName)
                .put("clientIP", clientIP)
                .build());

        Map result = cache.getIfPresent(body);
        if (result == null)
            result = getResult(body);
        if (result.get("error") != null)
            return createResponse(exc);
        boolean gate = (boolean) result.get("gate");

        if (gate) {
            cache.put(body, result);
            return Outcome.CONTINUE;
        }
        return createResponse(exc);
    }

    private Map getResult(String body) throws Exception {
        Exchange exc2 = httpClient.call(new Request.Builder().post(this.url).contentType(APPLICATION_JSON).body(
                body
        ).buildExchange());


        if(exc2.getResponse().getStatusCode() != 200)
            return ImmutableMap.of("error", "status " + exc2.getResponse().getStatusCode());

        return ImmutableMap.copyOf(om.readValue(exc2.getResponse().getBodyAsStreamDecoded(), Map.class));
    }

    private Outcome createResponse(Exchange exc) {
        exc.setResponse(Response.forbidden().build());
        return Outcome.RETURN;
    }

    public String getUrl() {
        return url;
    }

    @MCAttribute
    public void setUrl(String url) {
        this.url = url;
    }

    public HttpClientConfiguration getHttpClientConfiguration() {
        return httpClientConfiguration;
    }

    @MCChildElement(order = 10)
    public void setHttpClientConfiguration(HttpClientConfiguration httpClientConfiguration) {
        this.httpClientConfiguration = httpClientConfiguration;
    }
}
