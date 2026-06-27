/*
 *  Copyright 2024 predic8 GmbH, www.predic8.com
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

package com.predic8.membrane.core.openapi.serviceproxy;

import com.predic8.membrane.core.interceptor.flow.ReturnInterceptor;
import com.predic8.membrane.core.router.Router;
import com.predic8.membrane.core.router.TestRouter;
import com.predic8.membrane.core.util.URIFactory;
import com.predic8.membrane.shaded.io.swagger.v3.oas.models.OpenAPI;
import com.predic8.membrane.shaded.io.swagger.v3.oas.models.Operation;
import com.predic8.membrane.shaded.io.swagger.v3.oas.models.PathItem;
import com.predic8.membrane.shaded.io.swagger.v3.oas.models.media.Content;
import com.predic8.membrane.shaded.io.swagger.v3.oas.models.media.JsonSchema;
import com.predic8.membrane.shaded.io.swagger.v3.oas.models.parameters.RequestBody;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static com.predic8.membrane.core.openapi.serviceproxy.OpenAPISpec.YesNoOpenAPIOption.YES;
import static com.predic8.membrane.test.TestUtil.getPathFromResource;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class OpenAPI31ReferencesTest {

    static Router router;

    static APIProxy api;

    @BeforeAll
    public static void setUp() throws Exception {
        router = new TestRouter();
        router.getConfiguration().setUriFactory(new URIFactory());

        OpenAPISpec spec = new OpenAPISpec();
        spec.location = getPathFromResource( "openapi/specs/oas31/request-reference.yaml");
        spec.setValidateRequests(YES);

        api = new APIProxy();
        api.setPort(2000);
        api.setOpenapi(List.of(spec));
        router.add(api);

        APIProxy backend = new APIProxy();
        backend.setPort(3000);
        backend.getFlow().add(new ReturnInterceptor());
        router.add(backend);

        router.start();
    }

    @AfterAll
    public static void shutdown() {
        router.stop();
    }

    @Test
    void navigateThroughReferencedPartsOfDocument() {
        OpenAPI openAPI = api.apiRecords.get("demo-v1-0-0").getApi();
        PathItem pathItem = openAPI.getPaths().get("/users");
        assertNotNull(pathItem);
        Operation operation = pathItem.readOperations().getFirst();
        assertNotNull(operation);
        assertEquals("Demo", operation.getDescription());
        RequestBody requestBody = operation.getRequestBody();
        assertNotNull(requestBody);
        Content content = requestBody.getContent();
        assertNotNull(content);
        JsonSchema schema = (JsonSchema) content.get("application/json").getSchema();
        assertNotNull(schema);
        assertEquals("email", schema.getRequired().getFirst());
        assertEquals(Set.of("email","id","createdAt"), schema.getProperties().keySet());
    }

    @Test
    void validEmail() {
        given()
            .contentType("application/json")
            .body("""
                    {"email": "user@example.com"}""")
        .when()
            .post("http://localhost:2000/users")
        .then()
            .statusCode(200);
    }

    @Test
    void invalidEmail() {
        given()
            .contentType("application/json")
            .body("""
                    {"email": "invalid-email"}""")
        .when()
            .post("http://localhost:2000/users")
        .then()
            .statusCode(400)
            .body(containsString("is not a valid E-Mail"));
    }

    @Test
    void wrongPath() {
        given()
            .contentType("application/json")
            .body("{\"email\": \"user@example.com\"}")
        .when()
            .post("http://localhost:2000/wrong")
        .then()
            .statusCode(404);
    }
}
