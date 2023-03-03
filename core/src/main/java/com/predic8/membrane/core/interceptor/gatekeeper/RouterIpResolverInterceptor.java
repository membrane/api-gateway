/* Copyright 2020 predic8 GmbH, www.predic8.com

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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.config.security.SSLParser;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.transport.http.HttpClient;
import com.predic8.membrane.core.transport.http.HttpServerHandler;
import com.predic8.membrane.core.transport.http.client.HttpClientConfiguration;
import com.predic8.membrane.core.transport.ssl.SSLContext;
import com.predic8.membrane.core.transport.ssl.StaticSSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Checks, whether the exchange's remoteIp is one of the routers.
 * If yes, sends a POST to <a href="https://$router:$port/">...</a> with a body of {"port":$remotePort, "ip":$remoteIp}, expecting an HTTP 200 application/json {"ip":$realIp} in return.
 * If successful, replaces the exchange's remoteIp with the retrieved IP address.
 *
 * This interceptor is helpful in scenarios with multiple redundant routers for inbound HTTP requests.
 */
@MCElement(name = "routerIpResolver")
public class RouterIpResolverInterceptor extends AbstractInterceptor {

    private final Logger LOG = LoggerFactory.getLogger(RouterIpResolverInterceptor.class);

    private List<String> routerIps = new ArrayList<>();
    private ObjectMapper om = new ObjectMapper();
    private HttpClientConfiguration httpClientConfiguration;
    private SSLParser sslParser;
    private HttpClient httpClient;
    private SSLContext sslContext;
    private Outcome errorOutcome = Outcome.ABORT;
    private int port;

    public String getRouterIps() {
        return String.join(",", routerIps);
    }

    @MCAttribute
    public void setRouterIps(String routerIps) {
        this.routerIps = Arrays.asList(routerIps.split(","));
    }

    public HttpClientConfiguration getHttpClientConfiguration() {
        return httpClientConfiguration;
    }

    public int getPort() {
        return port;
    }

    /**
     * @default If not set (=0), the default port of the connection protocol (https) is used.
     */
    @MCAttribute
    public void setPort(int port) {
        this.port = port;
    }

    public Outcome getErrorOutcome() {
        return errorOutcome;
    }

    /**
     * @default ABORT
     */
    @MCAttribute
    public void setErrorOutcome(Outcome errorOutcome) {
        this.errorOutcome = errorOutcome;
    }

    @MCChildElement(order = 10)
    public void setHttpClientConfiguration(HttpClientConfiguration httpClientConfiguration) {
        this.httpClientConfiguration = httpClientConfiguration;
    }

    public SSLParser getSslParser() {
        return sslParser;
    }

    @MCChildElement(order = 20)
    public void setSslParser(SSLParser sslParser) {
        this.sslParser = sslParser;
    }

    @Override
    public void init(Router router) throws Exception {
        super.init(router);

        httpClient = router.getHttpClientFactory().createClient(httpClientConfiguration);
        if (sslParser != null)
            sslContext = new StaticSSLContext(sslParser, router.getResolverMap(), router.getBaseLocation());
    }

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        String remoteIp = exc.getRemoteAddrIp();
        if (!routerIps.contains(remoteIp))
            return Outcome.CONTINUE;

        try {
            int port = ((HttpServerHandler) exc.getHandler()).getSourceSocket().getPort();

            LOG.debug("remoteIp is a router, resolving port=" + port + " ip=" + exc.getRemoteAddrIp());

            String body = om.writeValueAsString(ImmutableMap.of("port", port, "ip", exc.getRemoteAddrIp()));

            Exchange exchange = new Request.Builder().post("https://" + remoteIp + (this.port == 0 ? "" : ":" + this.port)).body(body).buildExchange();
            if (sslContext != null)
                exchange.setProperty(Exchange.SSL_CONTEXT, sslContext);
            Response r = httpClient.call(exchange).getResponse();
            String res = r.getBodyAsStringDecoded();
            if (r.getStatusCode() == 200) {
                remoteIp = (String) om.readValue(res, Map.class).get("ip");
                exc.setRemoteAddrIp(remoteIp);
            } else {
                LOG.warn("Error during remote IP lookup on router " + remoteIp);
                return errorOutcome;
            }

            return Outcome.CONTINUE;
        } catch (Exception e) {
            LOG.error("", e);
            return errorOutcome;
        }
    }
}
