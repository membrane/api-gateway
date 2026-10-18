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

package com.predic8.membrane.core.openapi.serviceproxy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.router.DummyTestRouter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static com.predic8.membrane.core.http.Request.get;
import static com.predic8.membrane.core.interceptor.Outcome.RETURN;
import static com.predic8.membrane.core.openapi.util.OpenAPITestUtils.getApi;
import static org.junit.jupiter.api.Assertions.*;

class OpenAPIPublisherTest {

    private static final String ID = "customers-api-v1-0";

    private final ObjectMapper om = new ObjectMapper();

    private final OpenAPIPublisher publisher = new OpenAPIPublisher(
            Map.of(ID, new OpenAPIRecord(getApi(this, "/openapi/specs/customers.yml"), new OpenAPISpec())),
            "test publisher");

    @Test
    void wrongIdShowsDetailInProduction() throws Exception {
        final var exc = get("/api-docs/wrong-id").buildExchange();

        assertEquals(RETURN, new OpenAPIPublisher(Map.of(), "test publisher")
                .handle(exc, DummyTestRouter.productionRouter()));

        assertEquals(404, exc.getResponse().getStatusCode());
        assertEquals("OpenAPI document with the id wrong-id not found.", problem(exc).get("detail").asText());
    }

    @Test
    void notFoundReportsId() throws Exception {
        final var exc = get("/api-docs/wrong-id").buildExchange();

        publisher.handle(exc, new DummyTestRouter());

        final var problem = problem(exc);
        assertEquals("OpenAPI not found", problem.get("title").asText());
        assertEquals("wrong-id", problem.get("id").asText());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api-docs/ui", "/api-docs/ui/", "/api-doc/ui", "/api-docs/ui?x=1"})
    void swaggerUiWithoutIdAsksForId(String uri) throws Exception {
        final var exc = get(uri).buildExchange();

        assertEquals(RETURN, publisher.handle(exc, new DummyTestRouter()));

        assertEquals(404, exc.getResponse().getStatusCode());
        assertEquals("No OpenAPI document id", problem(exc).get("title").asText());
    }

    @Test
    void swaggerUiIgnoresQuery() throws Exception {
        final var exc = get("/api-docs/ui/" + ID + "?x=1").buildExchange();

        publisher.handle(exc, new DummyTestRouter());

        assertEquals(200, exc.getResponse().getStatusCode());
        assertTrue(exc.getResponse().getBodyAsStringDecoded().contains("/api-docs/" + ID));
    }

    @Test
    void documentIgnoresQuery() throws Exception {
        final var exc = get("/api-docs/" + ID + "?x=1").buildExchange();
        exc.setOriginalHostHeader("api.example.com:80");

        publisher.handle(exc, new DummyTestRouter());

        assertEquals(200, exc.getResponse().getStatusCode());
        assertEquals("application/x-yaml", exc.getResponse().getHeader().getContentType());
    }

    @Test
    void jsonOverviewWithoutTitle(@TempDir Path dir) throws Exception {
        final var location = dir.resolve("no-title.yml");
        Files.writeString(location, """
                openapi: 3.0.3
                info:
                  version: 1.0
                paths: {}
                """);
        final var spec = new OpenAPISpec();
        spec.setLocation(location.toString());
        final var router = new DummyTestRouter();
        final var exc = get("/api-docs").buildExchange();

        new OpenAPIPublisher(new OpenAPIRecordFactory(router).create(List.of(spec)), "test publisher").handle(exc, router);

        assertEquals(200, exc.getResponse().getStatusCode());
        final var overview = om.readTree(exc.getResponse().getBodyAsStringDecoded()).get("null-v1-0");
        assertTrue(overview.get("title").isNull());
        assertEquals("1.0", overview.get("version").asText());
    }

    private JsonNode problem(Exchange exc) throws Exception {
        return om.readTree(exc.getResponse().getBodyAsStringDecoded());
    }
}
