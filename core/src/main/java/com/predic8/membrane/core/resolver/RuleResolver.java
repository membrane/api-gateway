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

import com.google.common.collect.*;
import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.proxies.Proxy;
import com.predic8.membrane.core.proxies.*;
import com.predic8.membrane.core.util.functionalInterfaces.*;
import org.jetbrains.annotations.*;
import org.slf4j.*;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.stream.*;

public class RuleResolver implements SchemaResolver {

    private static final Logger log = LoggerFactory.getLogger(RuleResolver.class);

    final Router router;
    public RuleResolver(Router router) {
        this.router = router;
    }

    @Override
    public InputStream resolve(String urlString) {
        log.debug("Resolving from {}", urlString);
        URI uri = URI.create(urlString);
        String proxyName = uri.getHost();
        Proxy proxy = router.getRuleManager().getRuleByName(proxyName,Proxy.class);

        if (proxy == null)
            throw new RuntimeException("Rule with name '%s' not found".formatted(proxyName));

        if (!proxy.isActive())
            throw new RuntimeException("Rule with name '%s' not active".formatted(proxyName));

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
                throw new RuntimeException(e);
            }
        }

        if (!(proxy instanceof AbstractProxy p))
            throw new RuntimeException("Rule with name '" + proxyName + "' is not of type AbstractProxy");
        try {
            String pathAndQuery = getPathAndQuery(uri); // url.substring(8).split("/", 2)[1];
            Exchange exchange = new Request.Builder().get(pathAndQuery).buildExchange();
            RuleMatchingInterceptor.assignRule(exchange, p);
            List<Interceptor> additionalInterceptors = new ArrayList<>();

            if (p instanceof AbstractServiceProxy asp) {
                exchange.setDestinations(Stream.of(toUrl(asp.getTargetSSL() != null ? "https" : "http", asp.getHost(), asp.getTargetPort()).toString() + pathAndQuery).collect(Collectors.toList()));
                exchange.getRequest().getHeader().setHost(asp.getHost());

                HTTPClientInterceptor httpClientInterceptor = new HTTPClientInterceptor();
                httpClientInterceptor.init(router);
                additionalInterceptors.add(httpClientInterceptor);
            }

            additionalInterceptors.clear(); // TODO !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!

            router.getFlowController().invokeRequestHandlers(exchange, Stream.concat(p.getInterceptors().stream(), additionalInterceptors.stream()).toList());
            return exchange.getResponse().getBodyAsStream();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static @NotNull String getPathAndQuery(URI uri) {
        if (uri.getQuery().isEmpty())
            return uri.getPath();
        return uri.getPath() + "?" + uri.getQuery();
    }

    protected static String getRuleName(String url) {
        URI uri = URI.create(url);
        if (!uri.getScheme().equals("internal"))
            throw new RuntimeException("Not a service URL!");
        return uri.getHost();
    }

    public URL toUrl(String scheme, String host, int port) {
        try {
            return new URL(scheme + "://" + host + ":" + port);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
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
