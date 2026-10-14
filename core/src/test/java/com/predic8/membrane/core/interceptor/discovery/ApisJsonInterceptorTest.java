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

import java.util.*;

import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.interceptor.discovery.DiscoveryTestSupport.*;
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
}
