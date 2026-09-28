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

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.openapi.serviceproxy.*;
import com.predic8.membrane.core.proxies.*;
import com.predic8.membrane.core.router.*;
import com.predic8.membrane.test.*;
import org.junit.jupiter.api.*;

import java.time.*;
import java.util.*;

import static com.predic8.membrane.core.interceptor.discovery.CatalogEntry.ProxyKind.*;
import static org.junit.jupiter.api.Assertions.*;

class CatalogCollectorTest {

    private static final LocalDate DAY = LocalDate.of(2026, 1, 15);

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

    private static CatalogCollector collector(String configuredUrl, boolean collectOperations) {
        return new CatalogCollector("example.com", "gateway", "Gateway APIs", "All of them",
                configuredUrl, DAY, DAY, collectOperations);
    }

    private static Exchange exchange(String uri) throws Exception {
        return new Request.Builder().get(uri).header("Host", "gateway.example.com:2000")
                .buildExchange();
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
    void collectionMetadataComesFromTheConfiguration() throws Exception {
        Catalog catalog = collector().collect(router, exchange("/apis.json"));

        assertEquals("example.com:gateway", catalog.aid());
        assertEquals("Gateway APIs", catalog.name());
        assertEquals("All of them", catalog.description());
        assertEquals(DAY, catalog.created());
        assertEquals(DAY, catalog.modified());
        assertTrue(catalog.apis().isEmpty());
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
    void configuredUrlWinsOverTheRequest() throws Exception {
        CatalogCollector collector = collector("https://example.com/apis.json", false);

        assertEquals("https://example.com/apis.json",
                collector.collect(router, exchange("/apis.json")).url());
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

        assertEquals("example.com:fruit-shop-api-v2-0-0", entry.aid());
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

    @Test
    void identifiersAreSlugified() {
        assertEquals("0-0-0-0-80-baz", CatalogCollector.slug("*-0.0.0.0-*80/baz"));
        assertEquals("get-127-0-0-1-2000", CatalogCollector.slug("GET-127.0.0.1-2000"));
    }
}
