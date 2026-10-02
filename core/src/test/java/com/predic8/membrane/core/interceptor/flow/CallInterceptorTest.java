/* Copyright 2025 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.flow;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.templating.TemplateInterceptor;
import com.predic8.membrane.core.openapi.serviceproxy.APIProxy;
import com.predic8.membrane.core.openapi.serviceproxy.APIProxyKey;
import com.predic8.membrane.core.router.DefaultRouter;
import com.predic8.membrane.core.router.Router;
import com.predic8.membrane.core.transport.http.client.HttpClientConfiguration;
import com.predic8.membrane.core.transport.http.client.ProxyConfiguration;
import com.predic8.membrane.core.util.ConfigurationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;

import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.http.Request.get;
import static com.predic8.membrane.core.interceptor.flow.CallInterceptor.copyHeadersFromResponseToRequest;
import static org.junit.jupiter.api.Assertions.*;

class CallInterceptorTest {

    private Exchange exc;
    private Router router;

    @BeforeEach
    void setup() throws URISyntaxException {
        exc = get("/foo").buildExchange();
        exc.setProperty("a", "b");
        router = new DefaultRouter();
    }

    @AfterEach
    void teardown() {
        router.stop();
    }

    @Test
    void filterHeaders() {
        exc.setResponse(Response.ok()
                .header(TRANSFER_ENCODING, "foo")
                .header(CONTENT_ENCODING, "bar")
                .header(SERVER, "dummy")
                .header("X-FOO", "42").build());

        copyHeadersFromResponseToRequest(exc, exc);

        // preserve
        var header = exc.getRequest().getHeader();
        assertEquals("42", header.getFirstValue("X-FOO"));

        // take out
        assertNull(header.getFirstValue(SERVER));
        assertNull(header.getFirstValue(TRANSFER_ENCODING));
        assertNull(header.getFirstValue(CONTENT_ENCODING));
    }

    @Test
    void evaluateUrlTemplate() throws IOException {
        extracted("Path: /b");
    }

    @Test
    void urlTemplateAndAllowIllegalCharactersInURL() {
        router.getConfiguration().getUriFactory().setAllowIllegalCharacters(true);
        assertThrows(ConfigurationException.class, () -> extracted("dummy"));
    }

    @Test
    void usesRouterHttpClientConfig() throws Exception {
        var proxyApi = new APIProxy();
        proxyApi.setKey(new APIProxyKey(2001));
        proxyApi.getFlow().add(new TemplateInterceptor() {{
            setSrc("via proxy");
        }});
        proxyApi.getFlow().add(new ReturnInterceptor());
        router.add(proxyApi);

        var proxy = new ProxyConfiguration();
        proxy.setHost("localhost");
        proxy.setPort(2001);
        var httpClientConfig = new HttpClientConfiguration();
        httpClientConfig.setProxy(proxy);
        router.getConfiguration().setHttpClientConfig(httpClientConfig);
        router.start();

        var ci = new CallInterceptor();
        ci.setUrl("http://example.com/foo");
        ci.init(router);
        ci.handleRequest(exc);
        assertEquals("via proxy", exc.getRequest().getBodyAsStringDecoded());
    }

    @Test
    void elementHttpClientConfigOverridesRouterConfig() throws Exception {
        var proxyApi = new APIProxy();
        proxyApi.setKey(new APIProxyKey(2001));
        proxyApi.getFlow().add(new TemplateInterceptor() {{
            setSrc("via element proxy");
        }});
        proxyApi.getFlow().add(new ReturnInterceptor());
        router.add(proxyApi);
        router.start();

        var proxy = new ProxyConfiguration();
        proxy.setHost("localhost");
        proxy.setPort(2001);
        var httpClientConfig = new HttpClientConfiguration();
        httpClientConfig.setProxy(proxy);

        var ci = new CallInterceptor();
        ci.setUrl("http://example.com/foo");
        ci.setHttpClientConfig(httpClientConfig);
        ci.init(router);
        ci.handleRequest(exc);
        assertEquals("via element proxy", exc.getRequest().getBodyAsStringDecoded());
    }

    private void extracted(String expected) throws IOException {
        var api = new APIProxy();
        api.setKey(new APIProxyKey(2000));
        api.getFlow().add(new AbstractInterceptor() {
            @Override
            public Outcome handleRequest(Exchange exc) {
                System.out.println(exc);
                return super.handleRequest(exc);
            }
        });
        api.getFlow().add(new TemplateInterceptor() {{
            setSrc("Path: ${path}");
        }});
        api.getFlow().add(new ReturnInterceptor());
        router.add(api);
        router.start();

        var ci = new CallInterceptor();
        ci.setUrl("http://localhost:2000/${property.a}");
        ci.init(router);
        ci.handleRequest(exc);
        assertEquals(expected, exc.getRequest().getBodyAsStringDecoded());
    }
}