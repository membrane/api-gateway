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
package com.predic8.membrane.core.resolver;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.flow.ReturnInterceptor;
import com.predic8.membrane.core.interceptor.flow.invocation.testinterceptors.AbortFlowTestInterceptor;
import com.predic8.membrane.core.interceptor.templating.StaticInterceptor;
import com.predic8.membrane.core.proxies.InternalProxy;
import com.predic8.membrane.core.proxies.ServiceProxy;
import com.predic8.membrane.core.proxies.ServiceProxyKey;
import com.predic8.membrane.core.router.TestRouter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static org.junit.jupiter.api.Assertions.*;

class RuleResolverTest {

    private static final String WSDL_URL = "internal://doc/?wsdl";

    private TestRouter router;

    @BeforeEach
    void setUp() {
        router = new TestRouter();
        router.init();
    }

    @AfterEach
    void tearDown() {
        router.stop();
    }

    @Test
    void ruleName() {
        assertEquals("foo", RuleResolver.getRuleName("internal://foo"));
        assertEquals("foo", RuleResolver.getRuleName("internal://foo/b"));
        assertEquals("foo", RuleResolver.getRuleName("internal://foo/b/c"));
    }

    @Test
    void invalidUrl() {
        assertThrows( Exception.class, () -> RuleResolver.getRuleName("wrong://foo"));
    }

    @Test
    void resolvesTheDocumentTheFlowProduced() throws Exception {
        addProxy(staticText("<definitions/>"), new ReturnInterceptor());

        try (InputStream is = router.getResolverMap().resolve(WSDL_URL)) {
            assertEquals("<definitions/>", new String(is.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    /**
     * The flow has to answer the request itself. Without a response there is nothing to resolve,
     * which used to surface as a NullPointerException wrapped in a RuntimeException.
     */
    @Test
    void flowWithoutAResponseFails() {
        addProxy();

        var e = assertThrows(ResourceRetrievalException.class, () -> router.getResolverMap().resolve(WSDL_URL));
        assertTrue(e.getMessage().contains("doc"), e.getMessage());
        assertTrue(e.getMessage().contains("CONTINUE"), e.getMessage());
        assertTrue(e.getMessage().contains(WSDL_URL), e.getMessage());
    }

    @Test
    void abortedFlowFails() {
        addProxy(new AbortFlowTestInterceptor());

        var e = assertThrows(ResourceRetrievalException.class, () -> router.getResolverMap().resolve(WSDL_URL));
        assertTrue(e.getMessage().contains("aborted"), e.getMessage());
        assertTrue(e.getMessage().contains(WSDL_URL), e.getMessage());
    }

    @Test
    void unknownProxyFails() {
        var e = assertThrows(ResourceRetrievalException.class, () -> router.getResolverMap().resolve(WSDL_URL));
        assertTrue(e.getMessage().contains("not found"), e.getMessage());
    }

    @Test
    void inactiveProxyFails() throws Exception {
        router.add(proxy()); // Not initialized, so it never became active

        var e = assertThrows(ResourceRetrievalException.class, () -> router.getResolverMap().resolve(WSDL_URL));
        assertTrue(e.getMessage().contains("not active"), e.getMessage());
    }

    /**
     * A flow can set the response without returning. The document is resolved all the same.
     */
    @Test
    void resolvesTheDocumentOfAFlowThatDidNotReturn() throws Exception {
        addProxy(new AbstractInterceptor() {
            @Override
            public Outcome handleRequest(Exchange exc) {
                exc.setResponse(Response.ok("<definitions/>").build());
                return CONTINUE;
            }
        });

        try (InputStream is = router.getResolverMap().resolve(WSDL_URL)) {
            assertEquals("<definitions/>", new String(is.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test
    void resolvesThroughAnInternalProxy() throws Exception {
        InternalProxy proxy = new InternalProxy();
        proxy.setName("doc");
        proxy.getFlow().addAll(List.of(staticText("<definitions/>"), new ReturnInterceptor()));
        router.add(proxy);
        proxy.init(router);

        try (InputStream is = router.getResolverMap().resolve(WSDL_URL)) {
            assertEquals("<definitions/>", new String(is.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private void addProxy(Interceptor... flow) {
        ServiceProxy proxy = proxy();
        proxy.getFlow().addAll(List.of(flow));
        try {
            router.add(proxy);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        proxy.init(router);
    }

    private static ServiceProxy proxy() {
        ServiceProxy proxy = new ServiceProxy(new ServiceProxyKey("*", "*", null, 0), null, 0);
        proxy.setName("doc");
        return proxy;
    }

    private static StaticInterceptor staticText(String text) {
        StaticInterceptor si = new StaticInterceptor();
        si.setSrc(text);
        return si;
    }
}
