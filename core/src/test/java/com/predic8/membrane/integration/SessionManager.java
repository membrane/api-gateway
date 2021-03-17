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

package com.predic8.membrane.integration;

import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.RuleManager;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptorWithSession;
import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.session.JwtSessionManager;
import com.predic8.membrane.core.interceptor.session.InMemorySessionManager;
import com.predic8.membrane.core.rules.AbstractServiceProxy;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.rules.ServiceProxy;
import com.predic8.membrane.core.rules.ServiceProxyKey;
import com.predic8.membrane.core.transport.http.HttpClient;
import org.apache.http.Header;
import org.apache.http.client.CookieStore;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.config.Lookup;
import org.apache.http.cookie.CookieSpecProvider;
import org.apache.http.impl.client.*;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class SessionManager {

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> data() throws Exception {
        return Arrays.asList(new Object[][] {
                inMemory(),
                jwt()
        });
    }

    private static Object[] jwt() {
        return new Object[]{
                JwtSessionManager.class.getSimpleName(),
                (Supplier)() -> new JwtSessionManager()
        };
    }

    private static Object[] inMemory() {
        return new Object[]{
                InMemorySessionManager.class.getSimpleName(),
                (Supplier)() -> new InMemorySessionManager()
        };
    }

    @Parameterized.Parameter(value = 0)
    public String nameDummyField;

    @Parameterized.Parameter(value = 1)
    public Supplier<com.predic8.membrane.core.interceptor.session.SessionManager> smSupplier;

    public static final String REMEMBER_HEADER = "X-Remember-This";
    public static final int GATEWAY_PORT = 31337;

    @Test
    public void remembersThings() throws Exception{
        HttpRouter httpRouter = Util.basicRouter(Util.createServiceProxy(GATEWAY_PORT, testInterceptor()));

        HttpClientContext ctx = getHttpClientContext();

        String rememberThis = UUID.randomUUID().toString();
        String rememberThisFromServer = "";
        try(CloseableHttpClient client = getHttpClient()){

            try(CloseableHttpResponse resp = client.execute(RequestBuilder.get("http://localhost:" + GATEWAY_PORT).addHeader(REMEMBER_HEADER, rememberThis).build(),ctx)){

            }

            try(CloseableHttpResponse resp = client.execute(new HttpGet("http://localhost:" + GATEWAY_PORT),ctx)){
                rememberThisFromServer = resp.getFirstHeader(REMEMBER_HEADER).getValue();
            }
        }

        assertEquals(rememberThis,rememberThisFromServer);

        httpRouter.stop();
    }

    @Test
    public void sessionExpires() throws Exception{
        HttpRouter httpRouter = Util.basicRouter(Util.createServiceProxy(GATEWAY_PORT, testInterceptor(Duration.ZERO)));

        HttpClientContext ctx = getHttpClientContext();

        String rememberThis = UUID.randomUUID().toString();
        String rememberThisFromServer;
        try(CloseableHttpClient client = getHttpClient()){

            try(CloseableHttpResponse resp = client.execute(RequestBuilder.get("http://localhost:" + GATEWAY_PORT).addHeader(REMEMBER_HEADER, rememberThis).build(),ctx)){

            }

            try(CloseableHttpResponse resp = client.execute(new HttpGet("http://localhost:" + GATEWAY_PORT),ctx)){
                rememberThisFromServer = resp.getFirstHeader(REMEMBER_HEADER).getValue();
            }
        }

        assertNotEquals(rememberThis,rememberThisFromServer);
        assertEquals("null", rememberThisFromServer);

        httpRouter.stop();
    }

    @Test
    public void changeValueInSession() throws Exception{
        HttpRouter httpRouter = Util.basicRouter(Util.createServiceProxy(GATEWAY_PORT, testInterceptor()));

        HttpClientContext ctx = getHttpClientContext();

        String rememberThis = UUID.randomUUID().toString();
        String rememberThisFromServer;
        try(CloseableHttpClient client = getHttpClient()){

            try(CloseableHttpResponse resp = client.execute(RequestBuilder.get("http://localhost:" + GATEWAY_PORT).addHeader(REMEMBER_HEADER, rememberThis).build(),ctx)){

            }

            try(CloseableHttpResponse resp = client.execute(RequestBuilder.get("http://localhost:" + GATEWAY_PORT).addHeader(REMEMBER_HEADER, "rememberThis").build(),ctx)){
                if(nameDummyField.equals("jwt")){
                    List<Header> collect = Arrays.stream(resp.getHeaders("Set-Cookie")).collect(Collectors.toList());
                    assertEquals(2,collect.size());

                    assertTrue(collect.stream().filter(v -> v.getValue().toLowerCase().contains("Expires=Thu, 01 Jan 1970 00:00:00 GMT".toLowerCase())).count() == 1);
                }
            }

            try(CloseableHttpResponse resp = client.execute(new HttpGet("http://localhost:" + GATEWAY_PORT),ctx)){
                rememberThisFromServer = resp.getFirstHeader(REMEMBER_HEADER).getValue();
            }
        }

        assertEquals("rememberThis",rememberThisFromServer);

        httpRouter.stop();
    }

    private CloseableHttpClient getHttpClient() {
        return HttpClients.custom().setDefaultRequestConfig(RequestConfig.custom().setCookieSpec(CookieSpecs.STANDARD).build()).build();
    }

    private HttpClientContext getHttpClientContext() {
        BasicCookieStore cookieStore = new BasicCookieStore();
        HttpClientContext ctx = HttpClientContext.create();
        ctx.setAttribute(HttpClientContext.COOKIE_STORE, cookieStore);
        return ctx;
    }

    private AbstractInterceptorWithSession testInterceptor(Duration... ttl) {
        if(ttl == null || ttl.length == 0)
            ttl = new Duration[] {Duration.ofSeconds(300)};

        AbstractInterceptorWithSession result = new AbstractInterceptorWithSession() {
            @Override
            protected Outcome handleRequestInternal(Exchange exc) throws Exception {
                String rememberThis = exc.getRequest().getHeader().getFirstValue(REMEMBER_HEADER);
                if(rememberThis == null)
                    exc.setResponse(Response.ok().header(REMEMBER_HEADER,getSessionManager().getSession(exc).get(REMEMBER_HEADER)).build());
                else {
                    getSessionManager().getSession(exc).put(REMEMBER_HEADER,rememberThis);
                    exc.setResponse(Response.ok().build());
                }
                return Outcome.RETURN;
            }

            @Override
            protected Outcome handleResponseInternal(Exchange exc) throws Exception {
                return handleResponse(exc);
            }
        };

        result.setSessionManager(smSupplier.get());
        result.getSessionManager().setExpiresAfterSeconds(ttl[0].getSeconds());
        return result;
    }

}