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

class MembraneCatalogRendererTest {

    private static final ObjectMapper om = new ObjectMapper();

    private final MembraneCatalogRenderer renderer = new MembraneCatalogRenderer();

    private JsonNode render(Catalog catalog) throws Exception {
        return om.readTree(renderer.render(catalog));
    }

    @Test
    void declaresThatItReadsOperations() {
        assertTrue(renderer.rendersOperations());
    }

    @Test
    void servesPlainJson() {
        assertEquals(APPLICATION_JSON, renderer.contentType());
    }

    @Test
    void endpointCarriesTheRoutingDetailsTheOtherFormatsHide() throws Exception {
        JsonNode endpoint = render(catalog(describedApi())).path("apis").get(0).path("endpoint");

        assertEquals("https", endpoint.path("protocol").asText());
        assertEquals("example.com", endpoint.path("host").asText());
        assertEquals(443, endpoint.path("port").asInt());
        assertEquals("/shop/v2/", endpoint.path("path").asText());
        assertEquals("*", endpoint.path("method").asText());
        assertEquals("https://example.com/shop/v2/", endpoint.path("url").asText());
    }

    @Test
    void operationsCarryMethodPathAndSummary() throws Exception {
        JsonNode operations = render(catalog(describedApi())).path("apis").get(0).path("operations");

        assertEquals(2, operations.size());
        assertEquals("GET", operations.get(0).path("method").asText());
        assertEquals("/products", operations.get(0).path("path").asText());
        assertEquals("getProducts", operations.get(0).path("operationId").asText());
        assertEquals("Get all products", operations.get(0).path("summary").asText());
        assertEquals("Products", operations.get(0).path("tags").get(0).asText());
    }

    /**
     * Only deprecated operations carry the member, so the common case stays quiet.
     */
    @Test
    void deprecationIsOnlyRenderedWhenItIsTrue() throws Exception {
        JsonNode operations = render(catalog(describedApi())).path("apis").get(0).path("operations");

        assertTrue(operations.get(0).path("deprecated").isMissingNode());
        assertTrue(operations.get(1).path("deprecated").asBoolean());
    }

    @Test
    void contactLicenseAndTermsComeFromTheOpenApiInfoBlock() throws Exception {
        JsonNode api = render(catalog(describedApi())).path("apis").get(0);

        assertEquals("Predic8", api.path("contact").path("name").asText());
        assertEquals("info@predic8.de", api.path("contact").path("email").asText());
        assertEquals("Apache-2.0", api.path("license").path("name").asText());
        assertEquals("https://example.com/terms", api.path("termsOfService").asText());
    }

    @Test
    void theEntryOfAPlainProxyStaysShort() throws Exception {
        JsonNode api = render(catalog(plainProxy())).path("apis").get(0);

        assertEquals("serviceProxy", api.path("kind").asText());
        assertEquals("Shop", api.path("name").asText());
        assertTrue(api.path("version").isMissingNode());
        assertTrue(api.path("links").isMissingNode());
        assertTrue(api.path("tags").isMissingNode());
        assertTrue(api.path("contact").isMissingNode());
        assertTrue(api.path("license").isMissingNode());
        assertTrue(api.path("soap").isMissingNode());
        assertTrue(api.path("operations").isMissingNode());
    }

    @Test
    void collectionMetadataIsRendered() throws Exception {
        JsonNode document = render(catalog(describedApi()));

        assertEquals("example.com:gateway", document.path("aid").asText());
        assertEquals("Gateway APIs", document.path("name").asText());
        assertEquals("2026-01-15", document.path("created").asText());
        assertEquals("api", document.path("apis").get(0).path("kind").asText());
    }
}
