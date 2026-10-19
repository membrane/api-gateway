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

    private final ApisJsonRenderer renderer = new ApisJsonRenderer(COLLECTION, settings()
            .maintainers(List.of(new CatalogEntry.Contact("API Team", "api@example.com", "https://example.com/team")))
            .build());

    private static ApisJsonCollection.Builder settings() {
        return ApisJsonCollection.builder("gateway", DAY, DAY);
    }

    private static JsonNode render(ApisJsonCollection settings, Catalog catalog) throws Exception {
        return om.readTree(new ApisJsonRenderer(COLLECTION, settings).render(catalog));
    }

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
        assertEquals("0.24", document.path("specificationVersion").asText());
        assertEquals(1, document.path("apis").size());
    }

    /**
     * The APIs.json field list spells these members humanUrl and baseUrl, its examples and the
     * tooling built on them humanURL and baseURL. The renderer follows the examples.
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
        final var maintainer = render(settings()
                .maintainers(List.of(new CatalogEntry.Contact(null, "api@example.com", null))).build(), catalog())
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
        final var document = render(settings().build(), catalog());

        assertTrue(document.path("maintainers").isMissingNode());
    }

    @Test
    void typeAndKindAreRenderedWhenSet() throws Exception {
        final var document = render(settings().type("Index").kind("company").build(), catalog());

        assertEquals("Index", document.path("type").asText());
        assertEquals("company", document.path("kind").asText());
    }

    @Test
    void optionalCollectionMembersAreLeftOutWhenNotConfigured() throws Exception {
        final var document = render(settings().build(), catalog());

        for (final var member : List.of("type", "kind", "image", "position", "access", "common", "prompts", "rules",
                "workflows", "overlays", "include", "networks", "maintainers"))
            assertTrue(document.path(member).isMissingNode(), member);
    }

    @Test
    void configuredCollectionMembersAreRendered() throws Exception {
        final var document = render(settings()
                .image("https://example.com/logo.png")
                .position("Producer")
                .access("Public")
                .common(List.of(resource("Signup", "https://example.com/signup")))
                .prompts(List.of(resource("AgentPrompt", "https://example.com/prompt.md")))
                .rules(List.of(resource("SpectralRules", "https://example.com/rules.yml")))
                .workflows(List.of(resource("Arazzo", "https://example.com/arazzo.yml")))
                .overlays(List.of(new CatalogEntry.Reference("APIs.io Search", "https://example.com/search")))
                .include(List.of(new CatalogEntry.Reference("Partner APIs", "https://partner.example.com/apis.json")))
                .networks(List.of(new CatalogEntry.Reference("APIs.io", "https://apis.io/search")))
                .build(), catalog());

        assertEquals("https://example.com/logo.png", document.path("image").asText());
        assertEquals("Producer", document.path("position").asText());
        assertEquals("Public", document.path("access").asText());
        assertEquals("Signup", document.path("common").get(0).path("type").asText());
        assertEquals("https://example.com/signup", document.path("common").get(0).path("url").asText());
        assertEquals("AgentPrompt", document.path("prompts").get(0).path("type").asText());
        assertEquals("SpectralRules", document.path("rules").get(0).path("type").asText());
        assertEquals("Arazzo", document.path("workflows").get(0).path("type").asText());
        assertEquals("APIs.io Search", document.path("overlays").get(0).path("type").asText());
        assertEquals("https://example.com/search", document.path("overlays").get(0).path("url").asText());
        assertEquals("Partner APIs", document.path("include").get(0).path("name").asText());
        assertEquals("https://partner.example.com/apis.json", document.path("include").get(0).path("url").asText());
        assertEquals("APIs.io", document.path("networks").get(0).path("name").asText());
    }

    @Test
    void aResourceCarriesEveryMemberItHas() throws Exception {
        final var common = render(settings().common(List.of(new CatalogEntry.Resource("Pricing", "Plans",
                "Free and paid plans", "text/html", null, "Free up to 1000 calls", List.of("billing")))).build(), catalog())
                .path("common").get(0);

        assertEquals("Pricing", common.path("type").asText());
        assertEquals("Plans", common.path("name").asText());
        assertEquals("Free and paid plans", common.path("description").asText());
        assertEquals("text/html", common.path("mediaType").asText());
        assertEquals("Free up to 1000 calls", common.path("data").asText());
        assertTrue(common.path("url").isMissingNode());
        assertEquals(List.of("billing"), strings(common.path("tags")));
    }

    @Test
    void anApiCarriesItsConfiguredDetails() throws Exception {
        final var api = render(catalog(describedApi(new CatalogEntry.Details("https://example.com/shop.png",
                LocalDate.of(2025, 3, 1), LocalDate.of(2026, 2, 1),
                List.of(resource("Pricing", "https://example.com/pricing")),
                List.of(new CatalogEntry.Reference("APIs.io Search", "https://example.com/search")),
                List.of(resource("AgentPrompt", "https://example.com/prompt.md")),
                List.of(resource("SpectralRules", "https://example.com/rules.yml")),
                List.of(resource("Arazzo", "https://example.com/arazzo.yml"))))))
                .path("apis").get(0);

        assertEquals("https://example.com/shop.png", api.path("image").asText());
        assertEquals("2025-03-01", api.path("created").asText());
        assertEquals("2026-02-01", api.path("modified").asText());
        assertEquals("APIs.io Search", api.path("overlays").get(0).path("type").asText());
        assertEquals("AgentPrompt", api.path("prompts").get(0).path("type").asText());
        assertEquals("SpectralRules", api.path("rules").get(0).path("type").asText());
        assertEquals("Arazzo", api.path("workflows").get(0).path("type").asText());
    }

    @Test
    void configuredPropertiesFollowTheDerivedOnes() throws Exception {
        final var properties = render(catalog(describedApi(new CatalogEntry.Details(null, null, null,
                List.of(resource("Pricing", "https://example.com/pricing")), List.of(), List.of(), List.of(), List.of()))))
                .path("apis").get(0).path("properties");

        final var types = new ArrayList<String>();
        properties.forEach(property -> types.add(property.path("type").asText()));
        assertEquals(List.of("OpenAPI", "Documentation", "TermsOfService", "InterfaceLicense", "Pricing"), types);
    }

    @Test
    void anApiWithoutDetailsHasNoneOfTheirMembers() throws Exception {
        final var api = render(catalog(plainProxy())).path("apis").get(0);

        for (final var member : List.of("image", "created", "modified", "overlays", "prompts", "rules", "workflows", "contact"))
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
    void aContactCarriesEveryVCardMember() throws Exception {
        final var maintainer = render(settings().maintainers(List.of(new CatalogEntry.Contact("Kin Lane",
                "info@example.com", "https://example.com", "APIs.json", "Somewhere 1, Example City", "+1 555 0100",
                "apievangelist", "kinlane", "https://example.com/kin.png", "https://example.com/kin.vcf"))).build(),
                catalog()).path("maintainers").get(0);

        assertEquals("Kin Lane", maintainer.path("FN").asText());
        assertEquals("APIs.json", maintainer.path("org").asText());
        assertEquals("Somewhere 1, Example City", maintainer.path("adr").asText());
        assertEquals("+1 555 0100", maintainer.path("tel").asText());
        assertEquals("apievangelist", maintainer.path("X-twitter").asText());
        assertEquals("kinlane", maintainer.path("X-github").asText());
        assertEquals("https://example.com/kin.png", maintainer.path("photo").asText());
        assertEquals("https://example.com/kin.vcf", maintainer.path("vCard").asText());
    }

    private static CatalogEntry.Resource resource(String type, String url) {
        return new CatalogEntry.Resource(type, null, null, null, url, null, List.of());
    }

    private static List<String> strings(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(node -> values.add(node.asText()));
        return values;
    }
}
