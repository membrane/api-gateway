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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.core.router.DummyTestRouter;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static com.predic8.membrane.core.http.Request.get;
import static com.predic8.membrane.core.interceptor.Outcome.RETURN;
import static org.junit.jupiter.api.Assertions.assertEquals;

class OpenAPIPublisherTest {

    @Test
    void wrongIdShowsDetailInProduction() throws Exception {
        final var exc = get("/api-docs/wrong-id").buildExchange();

        assertEquals(RETURN, new OpenAPIPublisher(Map.of())
                .handleOverviewOpenAPIDoc(exc, DummyTestRouter.productionRouter(), LoggerFactory.getLogger(OpenAPIPublisherTest.class)));

        assertEquals(404, exc.getResponse().getStatusCode());
        assertEquals("OpenAPI document with the id wrong-id not found.",
                new ObjectMapper().readTree(exc.getResponse().getBodyAsStringDecoded()).get("detail").asText());
    }
}
