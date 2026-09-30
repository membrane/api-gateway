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

import java.util.*;

import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.interceptor.discovery.CatalogTestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class ApisJsonRendererTest {

    private static final ObjectMapper om = new ObjectMapper();

    private final ApisJsonRenderer renderer = new ApisJsonRenderer();

    private JsonNode render(Catalog catalog) throws Exception {
        return om.readTree(renderer.render(catalog));
    }

    @Test
    void declaresThatItIgnoresOperations() {
        assertFalse(renderer.rendersOperations());
    }

    @Test
    void servesPlainJson() {
        assertEquals(APPLICATION_JSON, renderer.contentType());
    }

    @Test
    void collectionCarriesTheMembersTheSpecificationRequires() throws Exception {
        JsonNode document = render(catalog(describedApi()));

        assertEquals("example.com:gateway", document.path("aid").asText());
        assertEquals("Gateway APIs", document.path("name").asText());
        assertEquals("Every API of this gateway", document.path("description").asText());
        assertEquals("https://example.com/.well-known/api-catalog", document.path("url").asText());
        assertEquals("2026-01-15", document.path("created").asText());
        assertEquals("2026-01-15", document.path("modified").asText());
        assertEquals("0.18", document.path("specificationVersion").asText());
        assertEquals(1, document.path("apis").size());
    }

    /**
     * APIs.json spells these members with a capitalised URL. A reader that follows the
     * specification drops anything else, which is what made the previous implementation's
     * humanUrl and baseUrl invisible.
     */
    @Test
    void urlMembersUseTheSpelledOutCapitalisation() throws Exception {
        JsonNode api = render(catalog(describedApi())).path("apis").get(0);

        assertEquals("https://example.com/api-docs/ui/fruit-shop-api-v2-0-0",
                api.path("humanURL").asText());
        assertEquals("https://example.com/shop/v2/", api.path("baseURL").asText());
        assertTrue(api.path("humanUrl").isMissingNode());
        assertTrue(api.path("baseUrl").isMissingNode());
    }

    @Test
    void apiCarriesIdentityVersionAndTags() throws Exception {
        JsonNode api = render(catalog(describedApi())).path("apis").get(0);

        assertEquals("example.com:fruit-shop-api-v2-0-0", api.path("aid").asText());
        assertEquals("Fruit Shop API", api.path("name").asText());
        assertEquals("Showcases REST API design", api.path("description").asText());
        assertEquals("2.0.0", api.path("version").asText());
        assertEquals(List.of("Products", "Orders"), strings(api.path("tags")));
    }

    /**
     * The properties array is how APIs.json points a consumer at a machine readable description.
     */
    @Test
    void propertiesLinkToTheOpenApiDocumentAndItsDocumentation() throws Exception {
        JsonNode properties = render(catalog(describedApi())).path("apis").get(0).path("properties");

        assertEquals(2, properties.size());
        assertEquals("OpenAPI", properties.get(0).path("type").asText());
        assertEquals("https://example.com/api-docs/fruit-shop-api-v2-0-0",
                properties.get(0).path("url").asText());
        assertEquals("Documentation", properties.get(1).path("type").asText());
        assertEquals("https://example.com/api-docs/ui/fruit-shop-api-v2-0-0",
                properties.get(1).path("url").asText());
    }

    @Test
    void anApiWithoutAnOpenApiDocumentOmitsTheOptionalMembers() throws Exception {
        JsonNode api = render(catalog(plainProxy())).path("apis").get(0);

        assertEquals("example.com:shop", api.path("aid").asText());
        assertEquals("http://example.com:2000/shop", api.path("baseURL").asText());
        assertTrue(api.path("properties").isMissingNode());
        assertTrue(api.path("humanURL").isMissingNode());
        assertTrue(api.path("version").isMissingNode());
        assertTrue(api.path("tags").isMissingNode());
    }

    @Test
    void anEmptyGatewayStillRendersAValidCollection() throws Exception {
        JsonNode document = render(catalog());

        assertTrue(document.path("apis").isArray());
        assertTrue(document.path("apis").isEmpty());
    }

    private static List<String> strings(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(node -> values.add(node.asText()));
        return values;
    }
}
