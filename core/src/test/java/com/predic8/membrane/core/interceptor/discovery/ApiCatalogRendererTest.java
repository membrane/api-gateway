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
import org.junit.jupiter.api.*;

import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.interceptor.discovery.CatalogTestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class ApiCatalogRendererTest {

    private static final ObjectMapper om = new ObjectMapper();

    private final ApiCatalogRenderer renderer = new ApiCatalogRenderer();

    private JsonNode render(Catalog catalog) throws Exception {
        return om.readTree(renderer.render(catalog));
    }

    @Test
    void declaresThatItIgnoresOperations() {
        assertFalse(renderer.rendersOperations());
    }

    @Test
    void servesLinksetWithTheProfileRfc9727Recommends() {
        assertEquals("application/linkset+json;profile=\"https://www.rfc-editor.org/info/rfc9727\"",
                renderer.contentType());
        assertTrue(renderer.contentType().startsWith(APPLICATION_LINKSET_JSON));
    }

    /**
     * RFC 9264: the document is one object whose sole member is linkset, an array of link context
     * objects.
     */
    @Test
    void documentIsALinksetAndNothingElse() throws Exception {
        JsonNode document = render(catalog(describedApi()));

        assertEquals(1, document.size());
        assertTrue(document.path("linkset").isArray());
    }

    @Test
    void theCatalogItselfBookmarksEveryApi() throws Exception {
        JsonNode context = render(catalog(describedApi(), plainProxy())).path("linkset").get(0);

        assertEquals("https://example.com/.well-known/api-catalog", context.path("anchor").asText());
        assertEquals(2, context.path("item").size());
        assertEquals("https://example.com/shop/v2/", context.path("item").get(0).path("href").asText());
        assertEquals("Fruit Shop API", context.path("item").get(0).path("title").asText());
        assertEquals("http://example.com:2000/shop", context.path("item").get(1).path("href").asText());
    }

    @Test
    void eachApiIsItsOwnLinkContextPointingAtSpecificationAndDocumentation() throws Exception {
        JsonNode context = render(catalog(describedApi())).path("linkset").get(1);

        assertEquals("https://example.com/shop/v2/", context.path("anchor").asText());
        assertEquals("https://example.com/api-docs/fruit-shop-api-v2-0-0",
                context.path("service-desc").get(0).path("href").asText());
        assertEquals(APPLICATION_X_YAML, context.path("service-desc").get(0).path("type").asText());
        assertEquals("https://example.com/api-docs/ui/fruit-shop-api-v2-0-0",
                context.path("service-doc").get(0).path("href").asText());
        assertEquals(TEXT_HTML, context.path("service-doc").get(0).path("type").asText());
    }

    /**
     * Every link relation holds an array, even with a single target.
     */
    @Test
    void linkRelationsAlwaysHoldArrays() throws Exception {
        JsonNode linkset = render(catalog(describedApi())).path("linkset");

        assertTrue(linkset.get(0).path("item").isArray());
        assertTrue(linkset.get(1).path("service-desc").isArray());
        assertTrue(linkset.get(1).path("service-doc").isArray());
    }

    /**
     * An API with nothing but its own URL adds no information beyond the bookmark above it.
     */
    @Test
    void anApiWithoutLinksGetsNoContextObjectOfItsOwn() throws Exception {
        JsonNode linkset = render(catalog(plainProxy())).path("linkset");

        assertEquals(1, linkset.size());
        assertEquals(1, linkset.get(0).path("item").size());
    }

    @Test
    void anEmptyGatewayStillRendersALinkset() throws Exception {
        JsonNode linkset = render(catalog()).path("linkset");

        assertEquals(1, linkset.size());
        assertTrue(linkset.get(0).path("item").isEmpty());
    }
}
