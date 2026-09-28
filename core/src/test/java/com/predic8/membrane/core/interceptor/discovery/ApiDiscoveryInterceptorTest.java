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
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.openapi.serviceproxy.*;
import com.predic8.membrane.core.proxies.*;
import com.predic8.membrane.core.router.*;
import com.predic8.membrane.core.util.*;
import com.predic8.membrane.test.*;
import org.junit.jupiter.api.*;

import java.util.*;

import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.interceptor.discovery.DiscoveryFormat.*;
import static org.junit.jupiter.api.Assertions.*;

class ApiDiscoveryInterceptorTest {

    private static final ObjectMapper om = new ObjectMapper();

    private DummyTestRouter router;
    private ServiceProxy shop;

    @BeforeEach
    void setUp() throws Exception {
        router = new DummyTestRouter();
        shop = new ServiceProxy(new ServiceProxyKey("*", "*", "/shop", 2000), null, 0);
        shop.setName("Shop");
        router.add(shop);
        router.init();
    }

    private ApiDiscoveryInterceptor interceptor(DiscoveryFormat format) {
        ApiDiscoveryInterceptor interceptor = new ApiDiscoveryInterceptor();
        interceptor.setFormat(format);
        interceptor.setRootDomain("example.com");
        interceptor.setCollectionId("gateway");
        interceptor.init(router);
        return interceptor;
    }

    private static Exchange request(String method) throws Exception {
        Exchange exc = new Request.Builder().method(method).uri("/apis.json")
                .header("Host", "gateway.example.com:2000").buildExchange();
        exc.setOriginalHostHeader("gateway.example.com:2000");
        return exc;
    }

    @Test
    void apisJsonIsTheDefaultFormat() throws Exception {
        assertEquals(APISJSON, new ApiDiscoveryInterceptor().getFormat());

        Exchange exc = request("GET");
        assertEquals(RETURN, interceptor(APISJSON).handleRequest(exc));

        assertEquals(APPLICATION_JSON, exc.getResponse().getHeader().getContentType());
        assertEquals("0.18", body(exc).path("specificationVersion").asText());
        assertEquals("example.com:gateway", body(exc).path("aid").asText());
    }

    @Test
    void apiCatalogRendersALinkset() throws Exception {
        Exchange exc = request("GET");
        interceptor(APICATALOG).handleRequest(exc);

        assertTrue(exc.getResponse().getHeader().getContentType().startsWith(APPLICATION_LINKSET_JSON));
        assertTrue(body(exc).path("linkset").isArray());
    }

    @Test
    void membraneFormatListsTheProxiesOfTheGateway() throws Exception {
        Exchange exc = request("GET");
        interceptor(MEMBRANE).handleRequest(exc);

        assertEquals(APPLICATION_JSON, exc.getResponse().getHeader().getContentType());
        assertEquals("Shop", body(exc).path("apis").get(0).path("name").asText());
        assertEquals("serviceProxy", body(exc).path("apis").get(0).path("kind").asText());
    }

    /**
     * Only the membrane format renders operations, so only it pays for walking the paths of every
     * OpenAPI document. This is the wiring between the renderer's declaration and the collector.
     */
    @Test
    void onlyTheMembraneFormatCollectsOperations() throws Exception {
        OpenAPISpec spec = new OpenAPISpec();
        spec.location = TestUtil.getPathFromResource("openapi/specs/fruitshop-api-v2-openapi-3.yml");
        APIProxy fruitshop = new APIProxy();
        fruitshop.setOpenapi(List.of(spec));
        fruitshop.setKey(new APIProxyKey(2001));
        router.add(fruitshop);
        router.init();

        Exchange withOperations = request("GET");
        interceptor(MEMBRANE).handleRequest(withOperations);
        assertFalse(apiNamed(withOperations, "Fruit Shop API").path("operations").isEmpty());

        Exchange withoutOperations = request("GET");
        interceptor(APISJSON).handleRequest(withoutOperations);
        assertTrue(body(withoutOperations).path("apis").isArray());
    }

    /**
     * RFC 9727 wants the catalog to advertise itself with the api-catalog link relation.
     */
    @Test
    void responseAdvertisesTheCatalogWithALinkHeader() throws Exception {
        Exchange exc = request("GET");
        exc.setProxy(shop);
        interceptor(APICATALOG).handleRequest(exc);

        assertEquals("<http://gateway.example.com:2000/apis.json>; rel=\"api-catalog\"",
                exc.getResponse().getHeader().getFirstValue(LINK));
    }

    @Test
    void headAnswersWithTheHeadersButNoDocument() throws Exception {
        Exchange exc = request("HEAD");
        interceptor(APICATALOG).handleRequest(exc);

        Response response = exc.getResponse();
        assertEquals(200, response.getStatusCode());
        assertTrue(response.getHeader().getContentType().startsWith(APPLICATION_LINKSET_JSON));
        assertEquals(0, response.getBody().getLength());
    }

    @Test
    void aMalformedDateIsARejectedConfiguration() {
        ApiDiscoveryInterceptor interceptor = new ApiDiscoveryInterceptor();

        ConfigurationException e =
                assertThrows(ConfigurationException.class, () -> interceptor.setCreated("15.01.2026"));
        assertTrue(e.getMessage().contains("created"));
        assertTrue(e.getMessage().contains("15.01.2026"));
        assertThrows(ConfigurationException.class, () -> interceptor.setModified("yesterday"));
    }

    @Test
    void configuredDatesAreKept() {
        ApiDiscoveryInterceptor interceptor = new ApiDiscoveryInterceptor();
        interceptor.setCreated("2026-01-15");
        interceptor.setModified("2026-02-20");

        assertEquals("2026-01-15", interceptor.getCreated().toString());
        assertEquals("2026-02-20", interceptor.getModified().toString());
    }

    private static JsonNode apiNamed(Exchange exc, String name) throws Exception {
        for (JsonNode api : body(exc).path("apis"))
            if (name.equals(api.path("name").asText()))
                return api;
        throw new AssertionError("No API named " + name + " in the document");
    }

    private static JsonNode body(Exchange exc) throws Exception {
        return om.readTree(exc.getResponse().getBodyAsStringDecoded());
    }
}
