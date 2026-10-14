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

import com.predic8.membrane.core.config.Path;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.openapi.serviceproxy.*;
import com.predic8.membrane.core.proxies.*;
import com.predic8.membrane.core.router.*;
import com.predic8.membrane.core.transport.http.*;
import com.predic8.membrane.test.*;
import org.junit.jupiter.api.*;

import java.util.*;

import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.interceptor.discovery.CatalogEntry.ProxyKind.*;
import static org.junit.jupiter.api.Assertions.*;

class CatalogCollectorTest {

    private DummyTestRouter router;

    @BeforeEach
    void setUp() {
        router = new DummyTestRouter();
    }

    private static CatalogCollector collector() {
        return collector(null, false);
    }

    private static CatalogCollector collectorWithOperations() {
        return collector(null, true);
    }

    private static CatalogCollector collector(String baseUrl, boolean collectOperations) {
        return new CatalogCollector(baseUrl, List.of(), collectOperations);
    }

    private static CatalogCollector collectorWithRules(TagRule... rules) {
        return new CatalogCollector(null, List.of(rules), false);
    }

    private static TagRule include(String tag) {
        final var rule = new TagRule.Include();
        rule.setTag(tag);
        return rule;
    }

    private static TagRule exclude(String tag) {
        final var rule = new TagRule.Exclude();
        rule.setTag(tag);
        return rule;
    }

    private static APIProxy taggedApi(String name, int port, String... tags) {
        final var api = new APIProxy();
        api.setKey(new APIProxyKey(port));
        api.setName(name);
        api.setTags(List.of(tags));
        return api;
    }

    private static ProxyInfo contact(String name, String email) {
        final var contact = new ProxyContact();
        contact.setName(name);
        contact.setEmail(email);
        final var info = new ProxyInfo();
        info.setContact(contact);
        return info;
    }

    private List<String> listedNames(CatalogCollector collector) throws Exception {
        return collector.collect(router, exchange("/apis.json")).apis().stream().map(CatalogEntry::name).toList();
    }

    private static CatalogCollector collectorWithBaseUrl(String baseUrl) {
        return collector(baseUrl, false);
    }

    private static Exchange exchange(String uri) throws Exception {
        return exchange(uri, "gateway.example.com:2000");
    }

    private static Exchange exchange(String uri, String hostHeader) throws Exception {
        return new Request.Builder().get(uri).header("Host", hostHeader).buildExchange();
    }

    private void add(Proxy proxy) throws Exception {
        router.add(proxy);
        router.init();
    }

    private static APIProxy apiProxy(String fixture, int port) {
        OpenAPISpec spec = new OpenAPISpec();
        spec.location = TestUtil.getPathFromResource("openapi/specs/" + fixture);
        APIProxy proxy = new APIProxy();
        proxy.setOpenapi(List.of(spec));
        proxy.setKey(new APIProxyKey(port));
        return proxy;
    }

    private static CatalogEntry only(Catalog catalog) {
        assertEquals(1, catalog.apis().size());
        return catalog.apis().getFirst();
    }

    @Test
    void anApiPublishingACatalogCarriesTheMediaTypeOfThatCatalog() throws Exception {
        final var catalogApi = taggedApi("APIs.json", 2000);
        catalogApi.getFlow().add(new ApisJsonInterceptor());
        add(catalogApi);
        add(taggedApi("Orders", 2001));

        final var apis = collector().collect(router, exchange("/apis.json")).apis();

        assertEquals(APPLICATION_JSON, apis.getFirst().catalogType());
        assertNull(apis.get(1).catalogType());
    }

    @Test
    void withoutABaseUrlTheDomainIsTheHostTheClientUsed() throws Exception {
        final var catalog = collector().collect(router, exchange("/apis.json"));

        assertEquals("gateway.example.com", catalog.domain());
        assertTrue(catalog.apis().isEmpty());
    }

    @Test
    void theDomainIsTheHostOfTheBaseUrl() throws Exception {
        assertEquals("api.example.com", collectorWithBaseUrl("https://api.example.com:8443/gateway")
                .collect(router, exchange("/apis.json")).domain());
    }

    @Test
    void catalogUrlIsDerivedFromTheRequestWithoutItsQueryString() throws Exception {
        ServiceProxy catalogApi = new ServiceProxy(new ServiceProxyKey(2000), null, 0);
        add(catalogApi);
        Exchange exc = exchange("/apis.json?pretty=true");
        exc.setProxy(catalogApi);

        assertEquals("http://gateway.example.com:2000/apis.json",
                collector().collect(router, exc).url());
    }

    @Test
    void theBaseUrlIsTheOriginOfTheCatalogUrl() throws Exception {
        assertEquals("https://api.example.com/apis.json", collectorWithBaseUrl("https://api.example.com/")
                .collect(router, exchange("/apis.json?pretty=true")).url());
    }

    /**
     * Behind a reverse proxy the client reaches every api under the public origin, whatever port
     * Membrane listens on, so the base URL replaces origin and port of an api bound to all hosts.
     */
    @Test
    void anApiBoundToEveryHostIsListedUnderTheBaseUrl() throws Exception {
        add(apiProxy("fruitshop-api-v2-openapi-3.yml", 2000));

        final var entry = only(collectorWithBaseUrl("https://api.example.com/gateway")
                .collect(router, exchange("/apis.json")));

        assertEquals("https://api.example.com/gateway/shop/v2/", entry.endpoint().url());
        assertEquals("https", entry.endpoint().protocol());
        assertEquals("api.example.com", entry.endpoint().host());
        assertEquals(443, entry.endpoint().port());
        assertEquals("https://api.example.com/gateway/api-docs/fruit-shop-api-v2-0-0", entry.links().specUrl());
        assertEquals("api.example.com:fruit-shop-api-v2-0-0", entry.aid());
    }

    @Test
    void anUppercaseSchemeInTheBaseUrlIsTheSameScheme() throws Exception {
        add(new ServiceProxy(new ServiceProxyKey("*", "*", "/shop", 2000), null, 0));

        final var endpoint = only(collectorWithBaseUrl("HTTPS://api.example.com")
                .collect(router, exchange("/apis.json"))).endpoint();

        assertEquals("https", endpoint.protocol());
        assertEquals(443, endpoint.port());
        assertEquals("https://api.example.com/shop", endpoint.url());
    }

    @Test
    void anApiBoundToOneHostKeepsItDespiteTheBaseUrl() throws Exception {
        add(new ServiceProxy(new ServiceProxyKey("shop.example.com", "*", "/shop", 2000), null, 0));

        assertEquals("http://shop.example.com:2000/shop", only(collectorWithBaseUrl("https://api.example.com")
                .collect(router, exchange("/apis.json"))).endpoint().url());
    }

    @Test
    void openApiMetadataIsCollectedPerDocument() throws Exception {
        add(apiProxy("fruitshop-api-v2-openapi-3.yml", 2000));

        CatalogEntry entry = only(collector().collect(router, exchange("/apis.json")));

        assertEquals(API, entry.kind());
        assertEquals("Fruit Shop API", entry.name());
        assertEquals("2.0.0", entry.version());
        assertTrue(entry.description().contains("Showcases REST API design"));
        assertEquals(List.of("Root", "Products", "Vendors", "Orders", "Customers"), entry.tags());
        assertEquals("Predic8", entry.contact().name());
        assertEquals("info@predic8.de", entry.contact().email());
    }

    @Test
    void operationsCarryMethodAndPath() throws Exception {
        add(apiProxy("fruitshop-api-v2-openapi-3.yml", 2000));

        List<CatalogOperation> operations =
                only(collectorWithOperations().collect(router, exchange("/apis.json"))).operations();

        CatalogOperation getProducts = operations.stream()
                .filter(operation -> "getProducts".equals(operation.operationId()))
                .findFirst().orElseThrow();
        assertEquals("GET", getProducts.method());
        assertEquals("/products", getProducts.path());
        assertEquals("Get all products", getProducts.summary());
        assertEquals(List.of("Products"), getProducts.tags());
        assertFalse(getProducts.deprecated());
        assertTrue(operations.stream().anyMatch(operation -> "POST".equals(operation.method())));
    }

    /**
     * Walking the paths of every OpenAPI document is the only part of collecting that grows with
     * the size of those documents, so formats that never render operations must not pay for it.
     */
    @Test
    void operationsAreSkippedWhenTheFormatDoesNotRenderThem() throws Exception {
        add(apiProxy("fruitshop-api-v2-openapi-3.yml", 2000));

        CatalogEntry entry = only(collector().collect(router, exchange("/apis.json")));

        assertTrue(entry.operations().isEmpty());
        assertEquals("Fruit Shop API", entry.name());
    }

    @Test
    void documentationLinksPointAtTheOpenApiPublisher() throws Exception {
        add(apiProxy("fruitshop-api-v2-openapi-3.yml", 2000));

        CatalogEntry entry = only(collector().collect(router, exchange("/apis.json")));

        assertEquals("gateway.example.com:fruit-shop-api-v2-0-0", entry.aid());
        assertEquals("http://gateway.example.com:2000/api-docs/fruit-shop-api-v2-0-0",
                entry.links().specUrl());
        assertEquals("http://gateway.example.com:2000/api-docs/ui/fruit-shop-api-v2-0-0",
                entry.links().humanUrl());
        assertEquals("http://gateway.example.com:2000/shop/v2/", entry.endpoint().url());
    }

    @Test
    void aProxyWithoutAnOpenApiDocumentGetsNoDeadLinks() throws Exception {
        ServiceProxy proxy = new ServiceProxy(new ServiceProxyKey("*", "*", "/shop", 2000), null, 0);
        proxy.setName("Shop");
        add(proxy);

        CatalogEntry entry = only(collector().collect(router, exchange("/apis.json")));

        assertEquals(SERVICE_PROXY, entry.kind());
        assertEquals("Shop", entry.name());
        assertEquals("Shop", entry.description());
        assertNull(entry.links().specUrl());
        assertNull(entry.links().humanUrl());
        assertTrue(entry.operations().isEmpty());
    }

    @Test
    void internalProxiesAreNotAdvertised() throws Exception {
        InternalProxy internal = new InternalProxy();
        internal.setName("worker");
        add(internal);

        assertTrue(collector().collect(router, exchange("/apis.json")).apis().isEmpty());
    }

    @Test
    void aWildcardHostIsReportedAsTheHostTheClientUsed() throws Exception {
        add(new ServiceProxy(new ServiceProxyKey("*", "*", "/shop", 2000), null, 0));

        CatalogEntry entry = only(collector().collect(router, exchange("/apis.json")));

        assertEquals("gateway.example.com", entry.endpoint().host());
    }

    @Test
    void aBracketedIpv6HostHeaderKeepsItsBrackets() throws Exception {
        add(new ServiceProxy(new ServiceProxyKey("*", "*", "/shop", 2000), null, 0));

        CatalogEntry entry = only(collector().collect(router, exchange("/apis.json", "[::1]")));

        assertEquals("[::1]", entry.endpoint().host());
        assertEquals("http://[::1]:2000/shop", entry.endpoint().url());
    }

    @Test
    void thePortAfterABracketedIpv6HostIsDropped() throws Exception {
        add(new ServiceProxy(new ServiceProxyKey("*", "*", "/shop", 2000), null, 0));

        assertEquals("[::1]", only(collector().collect(router,
                exchange("/apis.json", "[::1]:8080"))).endpoint().host());
    }

    @Test
    void aHostHeaderWithoutAPortIsUsedAsItIs() throws Exception {
        add(new ServiceProxy(new ServiceProxyKey("*", "*", "/shop", 2000), null, 0));

        assertEquals("gateway.example.com", only(collector().collect(router,
                exchange("/apis.json", "gateway.example.com"))).endpoint().host());
    }

    @Test
    void aConfiguredHostWinsOverTheHostHeader() throws Exception {
        add(new ServiceProxy(new ServiceProxyKey("shop.example.com", "*", "/shop", 2000), null, 0));

        assertEquals("shop.example.com",
                only(collector().collect(router, exchange("/apis.json"))).endpoint().host());
    }

    @Test
    void theDefaultPortForTheProtocolIsLeftOutOfTheUrl() throws Exception {
        add(new ServiceProxy(new ServiceProxyKey("*", "*", "/shop", 80), null, 0));

        assertEquals("http://gateway.example.com/shop",
                only(collector().collect(router, exchange("/apis.json"))).endpoint().url());
    }

    @Test
    void aNonDefaultPortStaysInTheUrl() throws Exception {
        add(new ServiceProxy(new ServiceProxyKey("*", "*", "/shop", 443), null, 0));

        assertEquals("http://gateway.example.com:443/shop",
                only(collector().collect(router, exchange("/apis.json"))).endpoint().url());
    }

    @Test
    void collectingWorksWithoutAHostHeaderAndWithoutAConnection() throws Exception {
        add(new ServiceProxy(new ServiceProxyKey("*", "*", "/shop", 2000), null, 0));
        Exchange exc = new Request.Builder().get("/apis.json").buildExchange();

        assertEquals("http://localhost:2000/shop",
                only(collector().collect(router, exc)).endpoint().url());
    }

    /**
     * The id is the identifier without the domain, so it stays the same whichever host the catalog
     * was fetched from. Membrane's own format keys its entries by it.
     */
    @Test
    void theIdOfAnEntryDoesNotDependOnTheHost() throws Exception {
        add(apiProxy("fruitshop-api-v2-openapi-3.yml", 2000));
        add(new ServiceProxy(new ServiceProxyKey("*", "*", "/shop", 2001), null, 0));

        final var viaHostHeader = collector().collect(router, exchange("/apis.json")).apis();
        final var viaBaseUrl = collectorWithBaseUrl("https://api.example.com").collect(router, exchange("/apis.json")).apis();

        final var ids = viaHostHeader.stream().map(CatalogEntry::id).sorted().toList();
        assertEquals(2, ids.size());
        assertTrue(ids.contains("fruit-shop-api-v2-0-0"));
        assertTrue(ids.stream().noneMatch(id -> id.contains("example")), ids.toString());
        assertEquals(ids, viaBaseUrl.stream().map(CatalogEntry::id).sorted().toList());
    }

    @Test
    void twoApisLoadingTheSameDocumentGetDistinctIdentifiers() throws Exception {
        add(apiProxy("fruitshop-api-v2-openapi-3.yml", 2000));
        add(apiProxy("fruitshop-api-v2-openapi-3.yml", 2011));

        final var apis = collector().collect(router, exchange("/apis.json")).apis();

        assertEquals(2, apis.size());
        assertEquals("fruit-shop-api-v2-0-0", apis.get(0).id());
        assertTrue(apis.get(1).id().startsWith("fruit-shop-api-v2-0-0-"), apis.get(1).id());
        assertTrue(apis.get(1).id().contains("2011"), apis.get(1).id());
        assertEquals("gateway.example.com:" + apis.get(1).id(), apis.get(1).aid());
    }

    @Test
    void twoApisWithTheSameConfiguredIdGetDistinctIdentifiers() throws Exception {
        final var first = apiProxy("fruitshop-api-v2-openapi-3.yml", 2000);
        first.setId("shop");
        add(first);
        final var second = apiProxy("fruitshop-api-v2-openapi-3.yml", 2011);
        second.setId("shop");
        add(second);

        final var apis = collector().collect(router, exchange("/apis.json")).apis();

        assertEquals("shop", apis.get(0).aid());
        assertNotEquals("shop", apis.get(1).aid());
        assertEquals(apis.get(1).id(), apis.get(1).aid());
    }

    @Test
    void withoutTagRulesEveryApiIsListed() throws Exception {
        add(taggedApi("Internal", 2000, "internal"));
        add(new ServiceProxy(new ServiceProxyKey("*", "*", "/shop", 2001), null, 0) {{ setName("Shop"); }});

        assertEquals(List.of("Internal", "Shop"), listedNames(collector()));
    }

    @Test
    void theFirstRuleMatchingATagOfTheApiDecides() throws Exception {
        add(taggedApi("Public", 2000, "external"));
        add(taggedApi("Both", 2001, "external", "internal"));
        add(taggedApi("Internal", 2002, "internal"));

        assertEquals(List.of("Public"), listedNames(collectorWithRules(exclude("internal"), include("external"))));
        assertEquals(List.of("Public", "Both"), listedNames(collectorWithRules(include("external"), exclude("internal"))));
    }

    @Test
    void aServiceProxyCarriesTagsAndContactToo() throws Exception {
        final var shop = new ServiceProxy(new ServiceProxyKey("*", "*", "/shop", 2000), null, 0);
        shop.setName("Shop");
        shop.setTags(List.of("external"));
        shop.setInfo(contact("Shop Team", "shop@example.com"));
        add(shop);
        add(taggedApi("Internal", 2001, "internal"));

        final var entries = collectorWithRules(include("external")).collect(router, exchange("/apis.json")).apis();

        assertEquals(1, entries.size());
        assertEquals(List.of("external"), entries.getFirst().tags());
        assertEquals("Shop Team", entries.getFirst().contact().name());
    }

    @Test
    void withTagRulesAnApiNoRuleMatchesIsLeftOut() throws Exception {
        add(taggedApi("Partner", 2000, "partner"));
        add(new ServiceProxy(new ServiceProxyKey("*", "*", "/shop", 2001), null, 0));

        assertTrue(listedNames(collectorWithRules(exclude("internal"))).isEmpty());
    }

    @Test
    void tagsAndContactConfiguredOnTheApiAreListed() throws Exception {
        final var api = taggedApi("Orders", 2000, "external", "internal");
        api.setInfo(contact("Christian", "cg@example.com"));
        add(api);

        final var entry = only(collector().collect(router, exchange("/apis.json")));

        assertEquals(List.of("external", "internal"), entry.tags());
        assertEquals("Christian", entry.contact().name());
        assertEquals("cg@example.com", entry.contact().email());
    }

    @Test
    void configuredTagsComeFirstAndTheConfiguredContactWinsOverTheDocument() throws Exception {
        final var api = apiProxy("fruitshop-api-v2-openapi-3.yml", 2000);
        api.setTags(List.of("external", "Products"));
        api.setInfo(contact("Gateway Team", null));
        add(api);

        final var entry = only(collector().collect(router, exchange("/apis.json")));

        assertEquals(List.of("external", "Products", "Root", "Vendors", "Orders", "Customers"), entry.tags());
        assertEquals("Gateway Team", entry.contact().name());
        assertNull(entry.contact().email());
    }

    @Test
    void withoutAProxyTheCatalogUrlComesFromTheHostHeader() throws Exception {
        assertEquals("http://gateway.example.com:2000/apis.json",
                collector().collect(router, exchange("/apis.json?pretty=true")).url());
    }

    @Test
    void theIdOfAnApiWithOneDocumentIsKeptVerbatim() throws Exception {
        APIProxy proxy = apiProxy("fruitshop-api-v2-openapi-3.yml", 2000);
        proxy.setId("shop");
        add(proxy);

        final var entry = only(collector().collect(router, exchange("/apis.json")));
        assertEquals("shop", entry.aid());
        assertEquals("shop", entry.id());
    }

    @Test
    void anApiWithAnIdAndSeveralDocumentsGetsOneIdentifierPerDocument() throws Exception {
        APIProxy proxy = apiProxy("fruitshop-api-v2-openapi-3.yml", 2000);
        OpenAPISpec second = new OpenAPISpec();
        second.location = TestUtil.getPathFromResource("openapi/specs/info-servers.yml");
        proxy.setOpenapi(List.of(proxy.getOpenapi().getFirst(), second));
        proxy.setId("shop");
        add(proxy);

        List<String> aids = collector().collect(router, exchange("/apis.json")).apis().stream()
                .map(CatalogEntry::aid).toList();

        assertEquals(List.of("shop:fruit-shop-api-v2-0-0", "shop:servers-1-api-v1-0"), aids.stream().sorted().toList());
    }

    @Test
    void aSoapProxyAdvertisesTheWsdlTheGatewayPublishesNotTheBackendOne() throws Exception {
        SOAPProxy proxy = new SOAPProxy() {{
            wsdl = TestUtil.getPathFromResource("validation/ArticleService.wsdl");
            key = new ServiceProxyKey(2000);
        }};
        proxy.setPath(new Path(false, "/articles"));
        add(proxy);

        CatalogEntry entry = only(collector().collect(router, exchange("/apis.json")));

        assertEquals(SOAP_PROXY, entry.kind());
        assertEquals("http://gateway.example.com:2000/articles?wsdl", entry.soap().wsdl());
    }

    @Test
    void aConnectionWithoutALocalAddressFallsBackToLocalhost() throws Exception {
        add(new ServiceProxy(new ServiceProxyKey("*", "*", "/shop", 2000), null, 0));
        Exchange exc = new Exchange(new FakeHttpHandler(2000));
        exc.setRequest(new Request.Builder().get("/apis.json").build());

        assertEquals("http://localhost:2000/shop",
                only(collector().collect(router, exc)).endpoint().url());
    }

    @Test
    void identifiersAreSlugified() {
        assertEquals("0-0-0-0-80-baz", CatalogCollector.slug("*-0.0.0.0-*80/baz"));
        assertEquals("get-127-0-0-1-2000", CatalogCollector.slug("GET-127.0.0.1-2000"));
    }
}
