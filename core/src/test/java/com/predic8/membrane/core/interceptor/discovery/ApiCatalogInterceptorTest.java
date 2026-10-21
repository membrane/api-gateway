/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.discovery;

import com.predic8.membrane.core.router.*;
import org.junit.jupiter.api.*;

import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.interceptor.discovery.DiscoveryTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class ApiCatalogInterceptorTest {

    private DummyTestRouter router;

    @BeforeEach
    void setUp() throws Exception {
        router = routerWithShop();
    }

    @Test
    void servesALinksetOfTheGateway() throws Exception {
        final var exc = request("GET");
        initialized(new ApiCatalogInterceptor(), router).handleRequest(exc);

        assertTrue(exc.getResponse().getHeader().getContentType().startsWith(APPLICATION_LINKSET_JSON));
        assertEquals("http://gateway.example.com:2000/shop",
                body(exc).path("linkset").get(0).path("item").get(0).path("href").asText());
    }

    /**
     * RFC 9727 wants the catalog to advertise itself with the api-catalog link relation.
     */
    @Test
    void advertisesTheCatalogWithALinkHeader() throws Exception {
        final var exc = request("GET");
        exc.setProxy(shop());
        initialized(new ApiCatalogInterceptor(), router).handleRequest(exc);

        assertEquals("<http://gateway.example.com:2000/apis.json>; rel=\"api-catalog\"", header(exc, LINK));
    }

    @Test
    void theLinkHeaderPointsAtTheBaseUrl() throws Exception {
        final var interceptor = new ApiCatalogInterceptor();
        interceptor.setBaseUrl("https://api.example.com");
        final var exc = request("HEAD");
        initialized(interceptor, router).handleRequest(exc);

        assertEquals("<https://api.example.com/apis.json>; rel=\"api-catalog\"", header(exc, LINK));
    }

    @Test
    void aBrowserGetsTheLinksetToo() throws Exception {
        final var exc = browserRequest();
        initialized(new ApiCatalogInterceptor(), router).handleRequest(exc);

        assertTrue(exc.getResponse().getHeader().getContentType().startsWith(APPLICATION_LINKSET_JSON));
        assertNull(header(exc, VARY));
    }
}
