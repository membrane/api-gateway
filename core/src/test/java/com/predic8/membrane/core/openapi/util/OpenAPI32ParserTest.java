/*
 *  Copyright 2026 predic8 GmbH, www.predic8.com
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.predic8.membrane.core.openapi.util;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.predic8.membrane.core.openapi.util.OpenAPI32Parser.*;
import static com.predic8.membrane.core.openapi.util.OpenAPITestUtils.parseOpenAPI32;
import static io.swagger.v3.oas.models.SpecVersion.V31;
import static org.junit.jupiter.api.Assertions.*;

class OpenAPI32ParserTest {

    OpenAPI api;

    @BeforeEach
    void setUp() {
        api = parseOpenAPI32(this, "/openapi/specs/oas32/query-method.yaml");
    }

    @Test
    void detectsVersion() {
        assertTrue(isOpenAPI32(OpenAPITestUtils.om.createObjectNode().put("openapi", "3.2.0")));
        assertFalse(isOpenAPI32(OpenAPITestUtils.om.createObjectNode().put("openapi", "3.1.0")));
    }

    @Test
    void keepsRealVersionButUsesV31Semantics() {
        // The real 3.2 version is reported, while the document is parsed with 3.1 (V31) semantics.
        assertEquals("3.2.0", api.getOpenapi());
        assertEquals(V31, api.getSpecVersion());
    }

    @Test
    void componentsAndRefsResolved() {
        assertTrue(api.getComponents().getSchemas().containsKey("SearchQuery"));
        assertTrue(api.getComponents().getSchemas().containsKey("Result"));
    }

    @Test
    void queryOperationAttached() {
        PathItem search = api.getPaths().get("/search");
        assertNull(search.getGet(), "QUERY must not leak into a standard method slot");

        Operation query = getAdditionalOperation(search, "QUERY");
        assertNotNull(query);
        assertEquals("Run a search with a request body",  query.getSummary());
        assertEquals("#/components/schemas/SearchQuery",
                query.getRequestBody().getContent().get("application/json").getSchema().get$ref());
    }

    @Test
    void additionalOperationAttached() {
        Operation purge = getAdditionalOperation(api.getPaths().get("/search"), "purge");
        assertNotNull(purge, "additionalOperations entries must be reachable case-insensitively");
        assertEquals("Purge the search index", purge.getSummary());
    }

    @Test
    void selfPreservedAsExtension() {
        assertEquals("https://example.com/search-api", api.getExtensions().get(X_MEMBRANE_SELF));
    }

    @Test
    void querystringParameterAttached() {
        var get = api.getPaths().get("/find").getGet();
        // Swagger drops the unknown in:querystring; the parser re-attaches it as an extension.
        assertTrue(get.getParameters() == null
                   || get.getParameters().stream().noneMatch(p -> "querystring".equals(p.getIn())));
        var param = getQuerystringParameter(api.getPaths().get("/find"), get);
        assertNotNull(param);
        assertEquals("querystring", param.getIn());
        assertNotNull(param.getContent().get("application/x-www-form-urlencoded"));
    }

    @Test
    void itemSchemaAttachedToSequentialMediaType() {
        var mediaType = api.getPaths().get("/bulk").getPost()
                .getRequestBody().getContent().get("application/jsonl");
        var itemSchema = getItemSchema(mediaType);
        assertNotNull(itemSchema, "itemSchema must be attached to the sequential media type");
        assertEquals("#/components/schemas/Document", itemSchema.get$ref());
    }
}
