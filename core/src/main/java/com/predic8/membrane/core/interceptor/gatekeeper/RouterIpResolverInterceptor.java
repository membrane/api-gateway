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

import com.fasterxml.jackson.databind.*;
import com.google.common.collect.*;
import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.config.security.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.transport.http.*;
import com.predic8.membrane.core.transport.http.client.*;
import com.predic8.membrane.core.transport.ssl.*;
import org.slf4j.*;

import java.util.*;

import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;

/**
 * Checks, whether the exchange's remoteIp is one of the routers.
 * If yes, sends a POST to <a href="https://$router:$port/">https://$router:$port/</a> with a body of {"port":$remotePort, "ip":$remoteIp}, expecting an HTTP 200 application/json {"ip":$realIp} in return.
 * If successful, replaces the exchange's remoteIp with the retrieved IP address.
 * <p>
 * This interceptor is helpful in scenarios with multiple redundant routers for inbound HTTP requests.
 */
@MCElement(name = "routerIpResolver")
public class RouterIpResolverInterceptor extends AbstractInterceptor {

    private final Logger LOG = LoggerFactory.getLogger(RouterIpResolverInterceptor.class);

    private List<String> routerIps = new ArrayList<>();
    private final ObjectMapper om = new ObjectMapper();
    private HttpClientConfiguration httpClientConfiguration;
    private SSLParser sslParser;
    private HttpClient httpClient;
    private SSLContext sslContext;
    private Outcome errorOutcome = Outcome.ABORT;
    private int port;

    public RouterIpResolverInterceptor() {
        name = "Router IP";
    }

    @Override
    public String getShortDescription() {
        return "Resolve actual IP addresses in redundant router configuration scenarios.";
    }

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
    public void init() {
        super.init();
        httpClient = router.getHttpClientFactory().createClient(httpClientConfiguration);
        if (sslParser != null)
            sslContext = new StaticSSLContext(sslParser, router.getResolverMap(), router.getBaseLocation());
    }

    @Override
    public Outcome handleRequest(Exchange exc) {
        String remoteIp = exc.getRemoteAddrIp();
        if (!routerIps.contains(remoteIp))
            return CONTINUE;

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

            return CONTINUE;
        } catch (Exception e) {
            LOG.error("", e);
            return errorOutcome;
        }
    }
}
