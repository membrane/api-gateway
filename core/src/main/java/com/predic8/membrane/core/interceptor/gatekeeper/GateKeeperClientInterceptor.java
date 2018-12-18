package com.predic8.membrane.core.interceptor.gatekeeper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.transport.http.HttpClient;
import com.predic8.membrane.core.transport.http.HttpServerHandler;
import com.predic8.membrane.core.transport.http.client.HttpClientConfiguration;

import java.net.HttpURLConnection;
import java.net.URL;

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
        if (httpClientConfiguration == null) {
            httpClientConfiguration = new HttpClientConfiguration();
        }
        httpClient = new HttpClient(httpClientConfiguration);
    }

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {

        String ruleName = exc.getRule().getName();
        String clientIP = exc.getRemoteAddrIp();


        Exchange exc2 = httpClient.call(new Request.Builder().post(this.url).body(
            om.writeValueAsString(ImmutableMap.builder()
                    .put("rule", ruleName)
                    .put("clientIP", clientIP)
                    .build())
        ).buildExchange());

        if(exc2.getResponse().getStatusCode() == 401)
            return Outcome.RETURN;
        return Outcome.CONTINUE;

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
