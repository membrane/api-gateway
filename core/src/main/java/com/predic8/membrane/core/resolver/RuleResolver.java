/* Copyright 2021 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.resolver;

import com.google.common.collect.Lists;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.interceptor.InternalRoutingInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.RuleMatchingInterceptor;
import com.predic8.membrane.core.proxies.AbstractProxy;
import com.predic8.membrane.core.proxies.InternalProxy;
import com.predic8.membrane.core.proxies.Proxy;
import com.predic8.membrane.core.router.Router;
import com.predic8.membrane.core.util.functionalInterfaces.ExceptionThrowingConsumer;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.net.URI;
import java.util.List;

import static com.predic8.membrane.core.interceptor.Outcome.ABORT;

public class RuleResolver implements SchemaResolver {

    private static final Logger log = LoggerFactory.getLogger(RuleResolver.class);

    final Router router;
    
    public RuleResolver(Router router) {
        this.router = router;
    }

    /**
     * Resolves an <code>internal://proxyName/path</code> URL by running the named proxy's flow and
     * returning the body of the response it produced. The flow has to answer the request itself,
     * for example with a <code>static</code> or <code>template</code> plugin: no call to the
     * proxy's target is made here.
     */
    @Override
    public InputStream resolve(String urlString) throws ResourceRetrievalException {
        log.debug("Resolving from {}", urlString);
        URI uri = URI.create(urlString);
        String proxyName = uri.getHost();
        Proxy proxy = router.getRuleManager().getRuleByName(proxyName,Proxy.class);

        if (proxy == null)
            throw new ResourceRetrievalException(urlString, "Proxy with name '%s' not found".formatted(proxyName));

        if (!proxy.isActive())
            throw new ResourceRetrievalException(urlString, "Proxy with name '%s' not active".formatted(proxyName));

        if (proxy instanceof InternalProxy ip) {
            log.debug("Resolving from internal proxy {}",ip);
            try {
                Exchange exc = Request.get("?wsdl").buildExchange();
                exc.getDestinations().clear();
                exc.getDestinations().add(urlString);
                exc.setProxy(proxy);
                InternalRoutingInterceptor isri = new InternalRoutingInterceptor();
                isri.init(router);
                isri.handleRequest(exc);
            } catch (Exception e) {
                log.debug("", e);
                throw new ResourceRetrievalException(urlString, e);
            }
        }

        if (!(proxy instanceof AbstractProxy p))
            throw new ResourceRetrievalException(urlString, "Proxy with name '%s' is not of type AbstractProxy".formatted(proxyName));
        try {
            Exchange exchange = new Request.Builder().get(getPathAndQuery(uri)).buildExchange();
            RuleMatchingInterceptor.assignRule(exchange, p);

            Outcome outcome = router.getFlowController().invokeRequestHandlers(exchange, p.getFlow());
            if (outcome == ABORT)
                throw new ResourceRetrievalException(urlString, "The flow of proxy '%s' aborted".formatted(proxyName));
            if (exchange.getResponse() == null)
                throw new ResourceRetrievalException(urlString, "The flow of proxy '%s' returned %s without producing a response".formatted(proxyName, outcome));

            return exchange.getResponse().getBodyAsStream();
        } catch (ResourceRetrievalException e) {
            throw e;
        } catch (Exception e) {
            throw new ResourceRetrievalException(urlString, e);
        }
    }

    private static @NotNull String getPathAndQuery(URI uri) {
        String query = uri.getQuery();
        if (query == null || query.isEmpty())
            return uri.getPath();
        return uri.getPath() + "?" + query;
    }

    protected static String getRuleName(String url) {
        URI uri = URI.create(url);
        if (!uri.getScheme().equals("internal"))
            throw new RuntimeException("Not a service URL!");
        return uri.getHost();
    }

    @Override
    public void observeChange(String url, ExceptionThrowingConsumer<InputStream> consumer) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public List<String> getChildren(String url) {
        return null;
    }

    @Override
    public long getTimestamp(String url) {
        return 0;
    }

    @Override
    public List<String> getSchemas() {
        return Lists.newArrayList("internal");
    }
}
