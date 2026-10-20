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

import java.time.*;
import java.util.*;

import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.interceptor.discovery.CatalogTestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class ApisJsonRendererTest {

    private static final ObjectMapper om = new ObjectMapper();

    private final ApisJsonRenderer renderer = new ApisJsonRenderer("gateway", COLLECTION, null, null, DAY, DAY,
            List.of(new CatalogEntry.Contact("API Team", "api@example.com", "https://example.com/team")));

    private static JsonNode render(ApisJsonRenderer renderer, Catalog catalog) throws Exception {
        return om.readTree(renderer.render(catalog));
    }

    private JsonNode render(Catalog catalog) throws Exception {
        return om.readTree(renderer.render(catalog));
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
        assertEquals("0.24", document.path("specificationVersion").asText());
        assertEquals(1, document.path("apis").size());
    }

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

    @Test
    void propertiesLinkToTheOpenApiDocumentAndItsDocumentation() throws Exception {
        JsonNode properties = render(catalog(describedApi())).path("apis").get(0).path("properties");

        assertEquals("OpenAPI", properties.get(0).path("type").asText());
        assertEquals("https://example.com/api-docs/fruit-shop-api-v2-0-0",
                properties.get(0).path("url").asText());
        assertEquals("Documentation", properties.get(1).path("type").asText());
        assertEquals("https://example.com/api-docs/ui/fruit-shop-api-v2-0-0",
                properties.get(1).path("url").asText());
    }

    @Test
    void termsOfServiceAndLicenseBecomeProperties() throws Exception {
        final var properties = render(catalog(describedApi())).path("apis").get(0).path("properties");

        assertEquals(4, properties.size());
        assertEquals("TermsOfService", properties.get(2).path("type").asText());
        assertEquals("https://example.com/terms", properties.get(2).path("url").asText());
        assertEquals("InterfaceLicense", properties.get(3).path("type").asText());
        assertEquals("Apache-2.0", properties.get(3).path("name").asText());
        assertEquals("https://www.apache.org/licenses/LICENSE-2.0", properties.get(3).path("url").asText());
    }

    @Test
    void aSoapProxyCarriesItsWsdlAsProperty() throws Exception {
        final var properties = render(catalog(soapProxy())).path("apis").get(0).path("properties");

        assertEquals(1, properties.size());
        assertEquals("WSDL", properties.get(0).path("type").asText());
        assertEquals("http://example.com:2000/cities?wsdl", properties.get(0).path("url").asText());
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
    void maintainersAreRenderedAsVCards() throws Exception {
        final var maintainers = render(catalog()).path("maintainers");

        assertEquals(1, maintainers.size());
        assertEquals("API Team", maintainers.get(0).path("FN").asText());
        assertEquals("api@example.com", maintainers.get(0).path("email").asText());
        assertEquals("https://example.com/team", maintainers.get(0).path("url").asText());
    }

    @Test
    void aMaintainerCarriesOnlyTheDetailsItHas() throws Exception {
        final var maintainer = render(new ApisJsonRenderer("gateway", COLLECTION, null, null, DAY, DAY,
                List.of(new CatalogEntry.Contact(null, "api@example.com", null))), catalog())
                .path("maintainers").get(0);

        assertEquals("api@example.com", maintainer.path("email").asText());
        assertTrue(maintainer.path("FN").isMissingNode());
        assertTrue(maintainer.path("url").isMissingNode());
    }

    @Test
    void collectionTagsAreTheTagsOfItsApisWithoutDuplicates() throws Exception {
        final var document = render(catalog(describedApi(), plainProxy(), describedApi()));

        assertEquals(List.of("Products", "Orders"), strings(document.path("tags")));
    }

    @Test
    void anEmptyGatewayStillRendersAValidCollection() throws Exception {
        JsonNode document = render(catalog());

        assertTrue(document.path("apis").isArray());
        assertTrue(document.path("apis").isEmpty());
        assertTrue(document.path("tags").isMissingNode());
    }

    @Test
    void withoutMaintainersTheCollectionHasNone() throws Exception {
        final var document = render(new ApisJsonRenderer("gateway", COLLECTION, null, null, DAY, DAY, List.of()), catalog());

        assertTrue(document.path("maintainers").isMissingNode());
    }

    @Test
    void typeAndKindAreRenderedWhenSet() throws Exception {
        final var document = render(new ApisJsonRenderer("gateway", COLLECTION, "Index", "company", DAY, DAY, List.of()), catalog());

        assertEquals("Index", document.path("type").asText());
        assertEquals("company", document.path("kind").asText());
    }

    @Test
    void withoutTypeAndKindTheCollectionHasNeither() throws Exception {
        final var document = render(catalog());

        assertTrue(document.path("type").isMissingNode());
        assertTrue(document.path("kind").isMissingNode());
    }

    @Test
    void anApiCarriesItsConfiguredDates() throws Exception {
        final var api = render(catalog(describedApi(new CatalogEntry.Details(LocalDate.of(2025, 3, 1), LocalDate.of(2026, 2, 1)))))
                .path("apis").get(0);

        assertEquals("2025-03-01", api.path("created").asText());
        assertEquals("2026-02-01", api.path("modified").asText());
    }

    @Test
    void anApiWithoutDetailsHasNoneOfTheirMembers() throws Exception {
        final var api = render(catalog(plainProxy())).path("apis").get(0);

        for (final var member : List.of("created", "modified", "contact"))
            assertTrue(api.path(member).isMissingNode(), member);
    }

    @Test
    void theContactOfAnApiIsAListOfOneVCard() throws Exception {
        final var contact = render(catalog(describedApi())).path("apis").get(0).path("contact");

        assertEquals(1, contact.size());
        assertEquals("Predic8", contact.get(0).path("FN").asText());
        assertEquals("info@predic8.de", contact.get(0).path("email").asText());
        assertEquals("https://www.predic8.de", contact.get(0).path("url").asText());
    }

    @Test
    void apisPublishingACatalogAreNotListed() throws Exception {
        final var document = render(catalog(describedApi(), apisJsonCatalog()));

        final var names = new ArrayList<String>();
        document.path("apis").forEach(api -> names.add(api.path("name").asText()));
        assertEquals(List.of("Fruit Shop API"), names);
    }

    @Test
    void theTagsOfAnApiPublishingACatalogDoNotBecomeCollectionTags() throws Exception {
        final var catalogApi = CatalogEntry.builder()
                .aid("example.com:apis-json").id("apis-json").kind(CatalogEntry.ProxyKind.API)
                .name("APIs.json").description("APIs.json")
                .endpoint(new CatalogEntry.Endpoint("https", "example.com", 443, "/apis.json", "*", "https://example.com/apis.json"))
                .tags(List.of("discovery"))
                .catalogType("application/json")
                .build();

        assertTrue(render(catalog(catalogApi)).path("tags").isMissingNode());
    }

    private static List<String> strings(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(node -> values.add(node.asText()));
        return values;
    }
}
