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
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.rules.*;
import com.predic8.membrane.core.util.functionalInterfaces.ExceptionThrowingConsumer;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RuleResolver implements SchemaResolver {

    final Router router;

    public RuleResolver(Router router) {
        this.router = router;
    }

    @Override
    public InputStream resolve(String url) throws ResourceRetrievalException {
        String ruleName = url.substring(8).split("/")[0];
        Rule rule = router.getRuleManager().getRuleByName(ruleName);

        if(rule == null)
            throw new RuntimeException("Rule with name '" + ruleName + "' not found");

        if(!rule.isActive())
            throw new RuntimeException("Rule with name '" + ruleName + "' not active");

        if(!(rule instanceof AbstractProxy))
            throw new RuntimeException("Rule with name '" + ruleName + "' is not of type AbstractProxy");

        AbstractProxy p = (AbstractProxy) rule;
        InterceptorFlowController interceptorFlowController = new InterceptorFlowController();
        try {
            String pathAndQuery = "/" + url.substring(8).split("/", 2)[1];
            Exchange exchange = new Request.Builder().get(pathAndQuery).buildExchange();
            RuleMatchingInterceptor.assignRule(exchange, p);
            List<Interceptor> additionalInterceptors = new ArrayList<>();

            if(p instanceof AbstractServiceProxy || p instanceof InternalProxy) {
                if (p instanceof AbstractServiceProxy) {
                    AbstractServiceProxy asp = (AbstractServiceProxy) p;
                    exchange.setDestinations(Stream.of(toUrl(asp.getTargetSSL() != null ? "https" : "http", asp.getHost(), asp.getTargetPort()).toString() + pathAndQuery).collect(Collectors.toList()));
                    exchange.getRequest().getHeader().setHost(asp.getHost());
                }
                if (p instanceof InternalProxy) {
                    InternalProxy ip = (InternalProxy) p;
                    exchange.setDestinations(Stream.of(toUrl(ip.getTarget()).toString() + pathAndQuery).collect(Collectors.toList()));
                    exchange.getRequest().getHeader().setHost(ip.getTarget().getHost());
                }

                HTTPClientInterceptor httpClientInterceptor = new HTTPClientInterceptor();
                httpClientInterceptor.init(router);
                additionalInterceptors.add(httpClientInterceptor);
            }

            interceptorFlowController.invokeHandlers(exchange, Stream.concat(p.getInterceptors().stream(), additionalInterceptors.stream()).collect(Collectors.toList()));
            return exchange.getResponse().getBodyAsStream();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public URL toUrl(String scheme, String host, int port){
        try {
            return new URL(scheme + "://" + host + ":" + port);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    public URL toUrl(AbstractServiceProxy.Target t){
        return toUrl(t.getSslParser() != null ? "https" : "http", t.getHost(), t.getPort());
    }

    @Override
    public void observeChange(String url, ExceptionThrowingConsumer<InputStream> consumer) throws ResourceRetrievalException {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public List<String> getChildren(String url) throws FileNotFoundException {
        return null;
    }

    @Override
    public long getTimestamp(String url) throws FileNotFoundException {
        return 0;
    }

    @Override
    public List<String> getSchemas() {
        return Lists.newArrayList("service");
    }
}
