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
import com.predic8.membrane.core.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;

import java.util.function.*;
import java.util.stream.*;

import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.interceptor.discovery.AbstractApiDiscoveryInterceptor.*;
import static com.predic8.membrane.core.interceptor.discovery.DiscoveryTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * What every format shares: collecting, HEAD, the well-known path and the base URL.
 */
class AbstractApiDiscoveryInterceptorTest {

    private DummyTestRouter router;

    @BeforeEach
    void setUp() throws Exception {
        router = routerWithShop();
    }

    @Test
    void headAnswersWithTheHeadersButNoDocument() throws Exception {
        final var exc = request("HEAD");
        assertEquals(RETURN, initialized(new ApiCatalogInterceptor(), router).handleRequest(exc));

        assertEquals(200, exc.getResponse().getStatusCode());
        assertTrue(exc.getResponse().getHeader().getContentType().startsWith(APPLICATION_LINKSET_JSON));
        assertEquals(0, exc.getResponse().getBody().getLength());
    }

    static Stream<Supplier<AbstractApiDiscoveryInterceptor>> nonLinksetFormats() {
        return Stream.of(ApisJsonInterceptor::new, ApiDiscoveryInterceptor::new);
    }

    /**
     * RFC 9727 makes the Linkset mandatory at the well-known URI. Any other format there would be
     * found by RFC clients and misread, so it is a configuration error.
     */
    @ParameterizedTest
    @MethodSource("nonLinksetFormats")
    void aNonLinksetFormatAtTheWellKnownPathIsARejectedConfiguration(Supplier<AbstractApiDiscoveryInterceptor> format) {
        final var interceptor = format.get();

        final var e = assertThrows(ConfigurationException.class,
                () -> interceptor.init(router, proxyAt(WELL_KNOWN_PATH)));
        assertTrue(e.getMessage().contains(WELL_KNOWN_PATH));
        assertTrue(e.getMessage().contains(interceptor.elementName()));
    }

    @Test
    void excludedApisAreLeftOut() throws Exception {
        final var interceptor = new ApisJsonInterceptor();
        interceptor.setExclude("Admin, Shop");
        final var exc = request("GET");
        initialized(interceptor, router).handleRequest(exc);

        assertEquals("Admin, Shop", interceptor.getExclude());
        assertTrue(body(exc).path("apis").isEmpty());
    }

    @Test
    void theResponseCarriesAnETag() throws Exception {
        final var exc = request("GET");
        initialized(new ApiCatalogInterceptor(), router).handleRequest(exc);

        final var etag = header(exc, ETAG);
        assertNotNull(etag);
        assertTrue(etag.matches("\"[0-9a-f]+\""), etag);
    }

    @Test
    void aMatchingIfNoneMatchGetsNotModified() throws Exception {
        final var interceptor = initialized(new ApiCatalogInterceptor(), router);
        final var first = request("GET");
        interceptor.handleRequest(first);
        final var etag = header(first, ETAG);

        final var again = request("GET");
        again.getRequest().getHeader().setValue(IF_NONE_MATCH, "\"other\", W/" + etag);
        interceptor.handleRequest(again);

        assertEquals(304, again.getResponse().getStatusCode());
        assertEquals(etag, header(again, ETAG));
        assertEquals(0, again.getResponse().getBody().getLength());
    }

    @Test
    void aStaleETagGetsTheDocument() throws Exception {
        final var exc = request("GET");
        exc.getRequest().getHeader().setValue(IF_NONE_MATCH, "\"stale\"");
        initialized(new ApiCatalogInterceptor(), router).handleRequest(exc);

        assertEquals(200, exc.getResponse().getStatusCode());
        assertTrue(body(exc).path("linkset").isArray());
    }

    @Test
    void headCarriesTheETagOfTheDocument() throws Exception {
        final var interceptor = initialized(new ApiCatalogInterceptor(), router);
        final var get = request("GET");
        interceptor.handleRequest(get);
        final var head = request("HEAD");
        interceptor.handleRequest(head);

        assertNotNull(header(get, ETAG));
        assertEquals(header(get, ETAG), header(head, ETAG));
    }

    @Test
    void theApiCatalogIsAcceptedAtTheWellKnownPath() {
        assertDoesNotThrow(() -> new ApiCatalogInterceptor().init(router, proxyAt(WELL_KNOWN_PATH)));
    }

    @Test
    void theElementNameComesFromTheConfigurationGrammar() {
        assertEquals("apisJson", new ApisJsonInterceptor().elementName());
        assertEquals("apiCatalog", new ApiCatalogInterceptor().elementName());
        assertEquals("apiDiscovery", new ApiDiscoveryInterceptor().elementName());
    }

    /**
     * Without a base URL, URLs and identifiers come from the Host header the client sent.
     */
    @Test
    void withoutABaseUrlTheHostHeaderIsUsed() throws Exception {
        final var exc = request("GET");
        exc.setProxy(shop());
        initialized(new ApisJsonInterceptor(), router).handleRequest(exc);

        assertEquals("gateway.example.com:apis", body(exc).path("aid").asText());
        assertEquals("http://gateway.example.com:2000/apis.json", body(exc).path("url").asText());
        assertEquals("http://gateway.example.com:2000/shop", body(exc).path("apis").get(0).path("baseURL").asText());
    }

    @Test
    void theBaseUrlReplacesTheHostHeader() throws Exception {
        final var interceptor = new ApisJsonInterceptor();
        interceptor.setBaseUrl("https://api.example.com");
        final var exc = request("GET");
        initialized(interceptor, router).handleRequest(exc);

        assertEquals("api.example.com:apis", body(exc).path("aid").asText());
        assertEquals("https://api.example.com/apis.json", body(exc).path("url").asText());
        assertEquals("https://api.example.com/shop", body(exc).path("apis").get(0).path("baseURL").asText());
    }

    @ParameterizedTest
    @ValueSource(strings = {"api.example.com", "ftp://api.example.com", "https://api.example.com/?a=b", "https://"})
    void aBaseUrlThatIsNoAbsoluteHttpUrlIsARejectedConfiguration(String baseUrl) {
        final var e = assertThrows(ConfigurationException.class, () -> new ApiCatalogInterceptor().setBaseUrl(baseUrl));
        assertTrue(e.getMessage().contains("apiCatalog/@baseUrl"));
    }

    @Test
    void aMalformedDateIsARejectedConfiguration() {
        final var interceptor = new ApisJsonInterceptor();

        final var e = assertThrows(ConfigurationException.class, () -> interceptor.setCreated("15.01.2026"));
        assertTrue(e.getMessage().contains("apisJson/@created"));
        assertTrue(e.getMessage().contains("15.01.2026"));
        assertThrows(ConfigurationException.class, () -> interceptor.setModified("yesterday"));
    }
}
