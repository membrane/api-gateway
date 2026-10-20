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
     * APIs.json is a standard format, so it answers with its JSON even in a browser.
     */
    @Test
    void answersEveryClientWithTheSameJson() throws Exception {
        final var exc = browserRequest();
        exc.setProxy(shop());
        interceptor().handleRequest(exc);

        assertEquals(APPLICATION_JSON, exc.getResponse().getHeader().getContentType());
        assertNull(header(exc, VARY));
    }

    @Test
    void typeKindDatesAndContactLoadFromYamlIntoTheDocument(@TempDir Path dir) throws Exception {
        final var file = dir.resolve("apis.yaml");
        Files.writeString(file, """
                api:
                  name: Orders
                  port: 2001
                  info:
                    created: 2025-03-01
                    modified: 2026-02-01
                    contact:
                      name: Orders Team
                      email: orders@example.com
                ---
                api:
                  port: 2000
                  flow:
                    - apisJson:
                        type: collection
                        kind: company
                """);
        final var yamlRouter = new DefaultRouter();
        try {
            loadIntoRouter(yamlRouter, file.toString());
            yamlRouter.init();
            final var apisJson = (ApisJsonInterceptor) yamlRouter.getRuleManager().getRules().get(1).getFlow().getFirst();
            final var exc = request("GET");
            apisJson.handleRequest(exc);
            final var document = body(exc);

            assertEquals("Collection", document.path("type").asText());
            assertEquals("company", document.path("kind").asText());
            final var orders = document.path("apis").get(0);
            assertEquals("Orders", orders.path("name").asText());
            assertEquals("2025-03-01", orders.path("created").asText());
            assertEquals("2026-02-01", orders.path("modified").asText());
            assertEquals("Orders Team", orders.path("contact").get(0).path("FN").asText());
            assertEquals("orders@example.com", orders.path("contact").get(0).path("email").asText());
        } finally {
            yamlRouter.stop();
        }
    }
}
