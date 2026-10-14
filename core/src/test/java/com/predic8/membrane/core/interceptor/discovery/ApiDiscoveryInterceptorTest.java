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

import com.fasterxml.jackson.databind.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.openapi.serviceproxy.*;
import com.predic8.membrane.core.router.*;
import com.predic8.membrane.test.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;

import java.util.*;

import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.interceptor.discovery.DiscoveryTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class ApiDiscoveryInterceptorTest {

    private DummyTestRouter router;

    @BeforeEach
    void setUp() throws Exception {
        router = routerWithShop();
    }

    private ApiDiscoveryInterceptor interceptor() {
        final var interceptor = new ApiDiscoveryInterceptor();
        return initialized(interceptor, router);
    }

    @Test
    void listsTheProxiesOfTheGateway() throws Exception {
        final var exc = request("GET");
        interceptor().handleRequest(exc);

        assertEquals(APPLICATION_JSON, exc.getResponse().getHeader().getContentType());
        assertEquals(MembraneCatalogRenderer.FORMAT_VERSION, body(exc).path("formatVersion").asText());
        assertTrue(body(exc).path("aid").isMissingNode());
        assertEquals("0-0-0-0-2000-shop", body(exc).path("apis").get(0).path("id").asText());
        assertEquals("Shop", body(exc).path("apis").get(0).path("name").asText());
        assertEquals("serviceProxy", body(exc).path("apis").get(0).path("kind").asText());
    }

    /**
     * Only this format renders operations, so only it pays for walking the paths of every OpenAPI
     * document. This is the wiring between the renderer's declaration and the collector.
     */
    @Test
    void operationsAreCollected() throws Exception {
        final var spec = new OpenAPISpec();
        spec.location = TestUtil.getPathFromResource("openapi/specs/fruitshop-api-v2-openapi-3.yml");
        final var fruitshop = new APIProxy();
        fruitshop.setOpenapi(List.of(spec));
        fruitshop.setKey(new APIProxyKey(2001));
        router.add(fruitshop);
        router.init();

        final var exc = request("GET");
        interceptor().handleRequest(exc);

        assertFalse(apiNamed(exc, "Fruit Shop API").path("operations").isEmpty());
    }

    @Test
    void doesNotClaimTheApiCatalogRelation() throws Exception {
        final var exc = request("GET");
        exc.setProxy(shop());
        interceptor().handleRequest(exc);

        assertNull(header(exc, LINK));
    }

    /**
     * The HTML page is the human readable view of the same catalog, operations included.
     */
    @Test
    void aBrowserGetsTheHtmlPage() throws Exception {
        final var exc = browserRequest();
        interceptor().handleRequest(exc);

        assertEquals(TEXT_HTML_UTF8, exc.getResponse().getHeader().getContentType());
        assertTrue(exc.getResponse().getBodyAsStringDecoded().contains("<h2>Shop"));
    }

    /**
     * The page shows text from OpenAPI documents the operator did not write, so it runs its own
     * script only, as a second line of defence behind the escaping.
     */
    @Test
    void theHtmlPageRunsNoScriptButItsOwn() throws Exception {
        final var html = browserRequest();
        interceptor().handleRequest(html);
        final var policy = header(html, CONTENT_SECURITY_POLICY);
        assertEquals(HtmlCatalogRenderer.CONTENT_SECURITY_POLICY, policy);
        assertTrue(policy.startsWith("default-src 'none'; style-src 'unsafe-inline'; script-src 'sha256-"));
        assertFalse(policy.contains("script-src 'unsafe-inline'"));

        final var json = request("GET");
        interceptor().handleRequest(json);
        assertNull(header(json, CONTENT_SECURITY_POLICY));
    }

    @Test
    void theFormatParameterGetsABrowserTheJson() throws Exception {
        final var exc = browserRequest();
        exc.getRequest().setUri("/catalog?format=json");
        interceptor().handleRequest(exc);

        assertEquals(APPLICATION_JSON, exc.getResponse().getHeader().getContentType());
    }

    @Test
    void mediaTypesInAcceptAreMatchedCaseInsensitively() throws Exception {
        assertEquals(TEXT_HTML_UTF8, contentTypeFor("TEXT/HTML"));
    }

    /**
     * HTML only wins when the client prefers it. <code>q=0</code> means "not this", and a lower
     * quality than JSON means JSON is preferred.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "*/*",
            "text/html;q=0",
            "application/json, text/html;q=0.5",
            "application/xhtml+xml",
            "not a media type"})
    void htmlIsOnlyServedWhenTheClientPrefersIt(String accept) throws Exception {
        assertEquals(APPLICATION_JSON, contentTypeFor(accept));
    }

    private String contentTypeFor(String accept) throws Exception {
        final var exc = request("GET");
        exc.getRequest().getHeader().setValue(ACCEPT, accept);
        interceptor().handleRequest(exc);
        return exc.getResponse().getHeader().getContentType();
    }

    /**
     * The answer is HTML or JSON depending on Accept, so caches must key on it.
     */
    @Test
    void theResponseVariesOnAccept() throws Exception {
        final var exc = request("GET");
        interceptor().handleRequest(exc);

        assertEquals(ACCEPT, header(exc, VARY));
    }

    private static JsonNode apiNamed(Exchange exc, String name) throws Exception {
        for (JsonNode api : body(exc).path("apis"))
            if (name.equals(api.path("name").asText()))
                return api;
        throw new AssertionError("No API named " + name + " in the document");
    }
}
