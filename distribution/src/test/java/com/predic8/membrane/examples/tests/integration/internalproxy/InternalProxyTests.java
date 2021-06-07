/*
 * Copyright 2021 predic8 GmbH, www.predic8.com
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

package com.predic8.membrane.examples.tests.integration.internalproxy;

import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.RuleManager;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.rules.*;
import com.predic8.membrane.core.transport.http.HttpClient;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class InternalProxyTests {

    public static final String X_COUNTER = "X-Counter";
    public static final int GATEWAY_PORT = 31337;

    @Test
    public void internalProxyCallingWorks() throws Exception{
        HttpRouter router = basicRouter(gateway("service:Returning internal proxy"), returningInternalProxy());

        HttpClient hc = new HttpClient();
        Exchange resp = hc.call(new Request.Builder().get("http://localhost:" + GATEWAY_PORT).buildExchange());

        assertEquals(200,resp.getResponse().getStatusCode());

        router.stop();
    }

    @Test
    public void internalProxyChainingWorks() throws Exception{
        HttpRouter router = basicRouter(gateway("service:Counting internal proxy 1"), countingInternalProxy("1"),returningInternalProxy());

        HttpClient hc = new HttpClient();
        Exchange resp = hc.call(new Request.Builder().get("http://localhost:" + GATEWAY_PORT).buildExchange());

        assertEquals(200,resp.getResponse().getStatusCode());
        assertEquals("1", resp.getResponse().getHeader().getFirstValue(X_COUNTER));

        router.stop();
    }

    public static Rule countingInternalProxy(String nameSuffix) {
        AbstractServiceProxy.Target target = new AbstractServiceProxy.Target();
        target.setUrl("service:Returning internal proxy");
        return createInternalProxy("Counting internal proxy " + nameSuffix, target, new AbstractInterceptor(){
            @Override
            public Outcome handleRequest(Exchange exc) throws Exception {
                String counterHeader = exc.getRequest().getHeader().getFirstValue(X_COUNTER);
                int counter = counterHeader == null ? 0 : Integer.parseInt(counterHeader);

                exc.getRequest().getHeader().setValue(X_COUNTER, String.valueOf(++counter));
                return super.handleRequest(exc);
            }
        });
    }

    public static Rule returningInternalProxy() {
        return createInternalProxy("Returning internal proxy", null, new AbstractInterceptor(){
            @Override
            public Outcome handleRequest(Exchange exc) throws Exception {
                exc.setResponse(Response.ok().header(X_COUNTER,exc.getRequest().getHeader().getFirstValue(X_COUNTER)).build());
                return Outcome.RETURN;
            }
        });
    }

    public static Rule gateway(String targetUrl) {
        AbstractServiceProxy.Target target = new AbstractServiceProxy.Target();
        target.setUrl(targetUrl);
        return createServiceProxy(GATEWAY_PORT, target);
    }

    public static HttpRouter basicRouter(Rule... rules){
        HttpRouter router = new HttpRouter();
        router.getTransport().setForceSocketCloseOnHotDeployAfter(1000);
        router.setHotDeploy(false);

        Arrays.stream(rules).forEach(rule -> router.getRuleManager().addProxy(rule, RuleManager.RuleDefinitionSource.MANUAL));

        router.start();
        return router;
    }

    public static Rule createInternalProxy(String name, AbstractServiceProxy.Target target, Interceptor... interceptors){
        InternalProxy internalProxy = new InternalProxy();
        internalProxy.setName(name);
        internalProxy.setInterceptors(Arrays.asList(interceptors));
        internalProxy.setTarget(target);
        return internalProxy;
    }

    public static Rule createServiceProxy(int listenPort, Interceptor... interceptors){
        return createServiceProxy(listenPort,null,interceptors);
    }

    public static Rule createServiceProxy(int listenPort, AbstractServiceProxy.Target target, Interceptor... interceptors){
        if (target == null)
            target = new AbstractServiceProxy.Target(null, -1);

        ServiceProxy sp = new ServiceProxy(new ServiceProxyKey(listenPort), null, -1);
        sp.setTarget(target);

        for (Interceptor interceptor : interceptors)
            sp.getInterceptors().add(interceptor);

        return sp;
    }
}
