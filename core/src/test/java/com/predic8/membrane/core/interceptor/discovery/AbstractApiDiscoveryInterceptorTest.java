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
import com.predic8.membrane.core.openapi.serviceproxy.*;
import com.predic8.membrane.core.proxies.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.*;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;

import java.nio.file.*;
import java.util.*;
import java.util.function.*;
import java.util.stream.*;

import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.interceptor.discovery.AbstractApiDiscoveryInterceptor.*;
import static com.predic8.membrane.core.interceptor.discovery.DiscoveryTestSupport.*;
import static com.predic8.membrane.core.router.YamlRouterBootstrap.*;
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
        assertEquals(RETURN, initialized(new ApisJsonInterceptor(), router).handleRequest(exc));

        assertEquals(200, exc.getResponse().getStatusCode());
        assertEquals(APPLICATION_JSON, exc.getResponse().getHeader().getContentType());
        assertEquals(0, exc.getResponse().getBody().getLength());
    }

    // RFC 9110 §8.6: HEAD and 304 may only announce the length the GET document has.
    @Test
    void headAndNotModifiedAnnounceTheLengthOfTheDocument() throws Exception {
        final var interceptor = initialized(new ApisJsonInterceptor(), router);
        final var get = request("GET");
        interceptor.handleRequest(get);
        final var length = get.getResponse().getBody().getLength();

        final var head = request("HEAD");
        interceptor.handleRequest(head);
        final var notModified = request("GET");
        notModified.getRequest().getHeader().setValue(IF_NONE_MATCH, header(get, ETAG));
        interceptor.handleRequest(notModified);

        assertEquals(length, head.getResponse().getHeader().getContentLength());
        assertEquals(0, head.getResponse().getBody().getLength());
        assertEquals(304, notModified.getResponse().getStatusCode());
        assertEquals(length, notModified.getResponse().getHeader().getContentLength());
        assertEquals(0, notModified.getResponse().getBody().getLength());
    }

    static Stream<Supplier<AbstractApiDiscoveryInterceptor>> nonLinksetFormats() {
        return Stream.of(ApisJsonInterceptor::new);
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
    void tagRulesSelectTheListedApis() throws Exception {
        final var rule = new TagRule.Include();
        rule.setTag("external");
        final var interceptor = new ApisJsonInterceptor();
        interceptor.setTags(List.of(rule));
        final var exc = request("GET");
        initialized(interceptor, router).handleRequest(exc);

        // Shop carries no tag, so no rule matches it.
        assertTrue(body(exc).path("apis").isEmpty());
    }

    @Test
    void tagsAndInfoOfProxiesAndTagRulesLoadFromYaml(@TempDir Path dir) throws Exception {
        final var file = dir.resolve("apis.yaml");
        Files.writeString(file, """
                api:
                  name: Orders
                  port: 2001
                  info:
                    contact:
                      name: Christian
                      email: cg@example.com
                  tags:
                    - external
                    - internal
                ---
                serviceProxy:
                  name: Shop
                  port: 2002
                  info:
                    contact:
                      name: Shop Team
                  tags:
                    - partner
                ---
                api:
                  port: 2000
                  flow:
                    - apisJson:
                        type: index
                        kind: topic
                        tags:
                          - exclude: internal
                          - include: external
                """);
        final var yamlRouter = new DefaultRouter();
        try {
            loadIntoRouter(yamlRouter, file.toString());
            final var rules = yamlRouter.getRuleManager().getRules();

            final var orders = assertInstanceOf(APIProxy.class, rules.getFirst());
            assertEquals(List.of("external", "internal"), orders.getTags());
            assertEquals("Christian", orders.getInfo().getContact().getName());
            assertEquals("cg@example.com", orders.getInfo().getContact().getEmail());

            final var shop = assertInstanceOf(ServiceProxy.class, rules.get(1));
            assertEquals(List.of("partner"), shop.getTags());
            assertEquals("Shop Team", shop.getInfo().getContact().getName());

            final var apisJson = assertInstanceOf(ApisJsonInterceptor.class, rules.get(2).getFlow().getFirst());
            assertInstanceOf(TagRule.Exclude.class, apisJson.getTags().get(0));
            assertEquals("internal", apisJson.getTags().get(0).getTag());
            assertInstanceOf(TagRule.Include.class, apisJson.getTags().get(1));
            assertEquals("external", apisJson.getTags().get(1).getTag());
            assertEquals(ApisJsonInterceptor.CollectionType.INDEX, apisJson.getType());
            assertEquals("topic", apisJson.getKind());
        } finally {
            yamlRouter.stop();
        }
    }

    @Test
    void theResponseCarriesAnETag() throws Exception {
        final var exc = request("GET");
        initialized(new ApisJsonInterceptor(), router).handleRequest(exc);

        final var etag = header(exc, ETAG);
        assertNotNull(etag);
        assertTrue(etag.matches("\"[0-9a-f]+\""), etag);
    }

    @Test
    void aMatchingIfNoneMatchGetsNotModified() throws Exception {
        final var interceptor = initialized(new ApisJsonInterceptor(), router);
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
        initialized(new ApisJsonInterceptor(), router).handleRequest(exc);

        assertEquals(200, exc.getResponse().getStatusCode());
        assertTrue(body(exc).path("apis").isArray());
    }

    @Test
    void headCarriesTheETagOfTheDocument() throws Exception {
        final var interceptor = initialized(new ApisJsonInterceptor(), router);
        final var get = request("GET");
        interceptor.handleRequest(get);
        final var head = request("HEAD");
        interceptor.handleRequest(head);

        assertNotNull(header(get, ETAG));
        assertEquals(header(get, ETAG), header(head, ETAG));
    }

    @Test
    void theElementNameComesFromTheConfigurationGrammar() {
        assertEquals("apisJson", new ApisJsonInterceptor().elementName());
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
        final var e = assertThrows(ConfigurationException.class, () -> new ApisJsonInterceptor().setBaseUrl(baseUrl));
        assertTrue(e.getMessage().contains("apisJson/@baseUrl"));
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
