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

import com.predic8.membrane.core.proxies.*;
import com.predic8.membrane.core.router.*;
import com.predic8.membrane.core.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.*;

import java.nio.file.*;
import java.util.*;

import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.interceptor.discovery.DiscoveryTestSupport.*;
import static com.predic8.membrane.core.router.YamlRouterBootstrap.*;
import static org.junit.jupiter.api.Assertions.*;

class ApisJsonInterceptorTest {

    private DummyTestRouter router;

    @BeforeEach
    void setUp() throws Exception {
        router = routerWithShop();
    }

    private static ProxyContact contact(String name, String email) {
        final var contact = new ProxyContact();
        contact.setName(name);
        contact.setEmail(email);
        return contact;
    }

    private ApisJsonInterceptor interceptor() {
        final var interceptor = new ApisJsonInterceptor();
        interceptor.setCollectionId("gateway");
        interceptor.setCollectionName("Gateway APIs");
        interceptor.setDescription("Every API of this gateway");
        interceptor.setCreated("2026-01-15");
        interceptor.setModified("2026-02-20");
        interceptor.setType(ApisJsonInterceptor.CollectionType.COLLECTION);
        interceptor.setKind("company");
        interceptor.setMaintainers(List.of(contact("API Team", "api@example.com")));
        return initialized(interceptor, router);
    }

    @Test
    void servesAnApisJsonCollectionOfTheGateway() throws Exception {
        final var exc = request("GET");
        assertEquals(RETURN, interceptor().handleRequest(exc));

        assertEquals(APPLICATION_JSON, exc.getResponse().getHeader().getContentType());
        final var document = body(exc);
        assertEquals("0.24", document.path("specificationVersion").asText());
        assertEquals("gateway.example.com:gateway", document.path("aid").asText());
        assertEquals("Gateway APIs", document.path("name").asText());
        assertEquals("Every API of this gateway", document.path("description").asText());
        assertEquals("2026-01-15", document.path("created").asText());
        assertEquals("2026-02-20", document.path("modified").asText());
        assertEquals("Collection", document.path("type").asText());
        assertEquals("company", document.path("kind").asText());
        assertEquals("Shop", document.path("apis").get(0).path("name").asText());
        assertEquals("API Team", document.path("maintainers").get(0).path("FN").asText());
        assertEquals("api@example.com", document.path("maintainers").get(0).path("email").asText());
    }

    @Test
    void theCollectionHasDefaultsForEverythingOptional() {
        final var interceptor = new ApisJsonInterceptor();

        assertEquals("apis", interceptor.getCollectionId());
        assertEquals("APIs", interceptor.getCollectionName());
        assertEquals("APIs served by this gateway", interceptor.getDescription());
        assertNotNull(interceptor.getCreated());
        assertNotNull(interceptor.getModified());
        assertTrue(interceptor.getMaintainers().isEmpty());
        assertNull(interceptor.getType());
        assertNull(interceptor.getKind());
    }

    /**
     * APIs.json is a standard format, so it answers with its JSON even in a browser, and does not
     * claim the RFC 9727 api-catalog relation, whose target must be a Linkset.
     */
    @Test
    void answersEveryClientWithTheSameJsonAndNoLink() throws Exception {
        final var exc = browserRequest();
        exc.setProxy(shop());
        interceptor().handleRequest(exc);

        assertEquals(APPLICATION_JSON, exc.getResponse().getHeader().getContentType());
        assertNull(header(exc, VARY));
        assertNull(header(exc, LINK));
    }

    @Test
    void everySpecificationMemberLoadsFromYamlIntoTheDocument(@TempDir Path dir) throws Exception {
        final var file = dir.resolve("apis.yaml");
        Files.writeString(file, """
                api:
                  name: Orders
                  port: 2001
                  info:
                    image: https://example.com/orders.png
                    created: 2025-03-01
                    modified: 2026-02-01
                    contact:
                      name: Orders Team
                      organization: Example Inc.
                      address: Example Street 1
                      phone: +1 555 0100
                      twitter: example
                      github: example-orders
                      photo: https://example.com/team.png
                      vCard: https://example.com/team.vcf
                    properties:
                      - type: Pricing
                        name: Plans
                        description: Free and paid plans
                        mediaType: text/html
                        url: https://example.com/pricing
                        tags:
                          - billing
                    overlays:
                      - type: APIs.io Search
                        url: https://example.com/search
                    prompts:
                      - type: AgentPrompt
                        data: List my open orders.
                    rules:
                      - type: SpectralRules
                        url: https://example.com/rules.yml
                    workflows:
                      - type: Arazzo
                        url: https://example.com/arazzo.yml
                ---
                api:
                  port: 2000
                  flow:
                    - apisJson:
                        image: https://example.com/logo.png
                        type: collection
                        kind: company
                        position: Producer
                        access: Public
                        maintainers:
                          - name: API Team
                            twitter: apiteam
                        common:
                          - type: Signup
                            url: https://example.com/signup
                        prompts:
                          - type: Prompt
                            url: https://example.com/prompt.md
                        rules:
                          - type: GovernanceRules
                            url: https://example.com/governance
                        workflows:
                          - type: Workflow
                            url: https://example.com/workflow
                        overlays:
                          - type: API Evangelist Ratings
                            url: https://example.com/ratings
                        include:
                          - name: Partner APIs
                            url: https://partner.example.com/.well-known/apis.json
                        networks:
                          - name: APIs.io
                            url: https://apis.io/search
                """);
        final var yamlRouter = new DefaultRouter();
        try {
            loadIntoRouter(yamlRouter, file.toString());
            yamlRouter.init();
            final var apisJson = (ApisJsonInterceptor) yamlRouter.getRuleManager().getRules().get(1).getFlow().getFirst();
            final var exc = request("GET");
            apisJson.handleRequest(exc);
            final var document = body(exc);

            assertEquals("https://example.com/logo.png", document.path("image").asText());
            assertEquals("Collection", document.path("type").asText());
            assertEquals("company", document.path("kind").asText());
            assertEquals("Producer", document.path("position").asText());
            assertEquals("Public", document.path("access").asText());
            assertEquals("apiteam", document.path("maintainers").get(0).path("X-twitter").asText());
            assertEquals("Signup", document.path("common").get(0).path("type").asText());
            assertEquals("Prompt", document.path("prompts").get(0).path("type").asText());
            assertEquals("GovernanceRules", document.path("rules").get(0).path("type").asText());
            assertEquals("Workflow", document.path("workflows").get(0).path("type").asText());
            assertEquals("API Evangelist Ratings", document.path("overlays").get(0).path("type").asText());
            assertEquals("Partner APIs", document.path("include").get(0).path("name").asText());
            assertEquals("APIs.io", document.path("networks").get(0).path("name").asText());

            final var orders = document.path("apis").get(0);
            assertEquals("Orders", orders.path("name").asText());
            assertEquals("https://example.com/orders.png", orders.path("image").asText());
            assertEquals("2025-03-01", orders.path("created").asText());
            assertEquals("2026-02-01", orders.path("modified").asText());
            final var pricing = orders.path("properties").get(0);
            assertEquals("Pricing", pricing.path("type").asText());
            assertEquals("Plans", pricing.path("name").asText());
            assertEquals("Free and paid plans", pricing.path("description").asText());
            assertEquals("text/html", pricing.path("mediaType").asText());
            assertEquals("https://example.com/pricing", pricing.path("url").asText());
            assertEquals("billing", pricing.path("tags").get(0).asText());
            assertEquals("APIs.io Search", orders.path("overlays").get(0).path("type").asText());
            assertEquals("List my open orders.", orders.path("prompts").get(0).path("data").asText());
            assertEquals("SpectralRules", orders.path("rules").get(0).path("type").asText());
            assertEquals("Arazzo", orders.path("workflows").get(0).path("type").asText());
            final var contact = orders.path("contact").get(0);
            assertEquals("Orders Team", contact.path("FN").asText());
            assertEquals("Example Inc.", contact.path("org").asText());
            assertEquals("Example Street 1", contact.path("adr").asText());
            assertEquals("+1 555 0100", contact.path("tel").asText());
            assertEquals("example", contact.path("X-twitter").asText());
            assertEquals("example-orders", contact.path("X-github").asText());
            assertEquals("https://example.com/team.png", contact.path("photo").asText());
            assertEquals("https://example.com/team.vcf", contact.path("vCard").asText());
        } finally {
            yamlRouter.stop();
        }
    }

    @Test
    void aResourceWithoutUrlOrDataIsARejectedConfiguration() {
        final var signup = new CatalogResource.Common();
        signup.setType("Signup");
        final var interceptor = new ApisJsonInterceptor();
        interceptor.setCommon(List.of(signup));

        final var e = assertThrows(ConfigurationException.class, () -> interceptor.init(router));
        assertTrue(e.getMessage().contains("Signup"));
    }

    @Test
    void anIncludeWithoutUrlIsARejectedConfiguration() {
        final var partner = new CatalogReference.Include();
        partner.setName("Partner APIs");
        final var interceptor = new ApisJsonInterceptor();
        interceptor.setInclude(List.of(partner));

        assertThrows(ConfigurationException.class, () -> interceptor.init(router));
    }
}
