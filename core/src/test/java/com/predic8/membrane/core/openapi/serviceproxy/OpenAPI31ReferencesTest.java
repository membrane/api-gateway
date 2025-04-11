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

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.interceptor.flow.*;
import com.predic8.membrane.core.util.*;
import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.parameters.*;
import org.junit.jupiter.api.*;

import java.util.*;

import static com.predic8.membrane.core.openapi.serviceproxy.OpenAPISpec.YesNoOpenAPIOption.*;
import static com.predic8.membrane.test.TestUtil.getPathFromResource;
import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

public class OpenAPI31ReferencesTest {

    static Router router;

    static APIProxy api;

    @BeforeAll
    public static void setUp() throws Exception {
        router = new HttpRouter();
        router.setUriFactory(new URIFactory());

        OpenAPISpec spec = new OpenAPISpec();
        spec.location = getPathFromResource( "openapi/specs/oas31/request-reference.yaml");
        spec.setValidateRequests(YES);

        api = new APIProxy();
        api.setPort(2000);
        api.setSpecs(List.of(spec));
        router.getRuleManager().addProxyAndOpenPortIfNew(api);

        APIProxy backend = new APIProxy();
        backend.setPort(3000);
        backend.getInterceptors().add(new ReturnInterceptor());
        router.getRuleManager().addProxyAndOpenPortIfNew(backend);

        router.init();
    }

    @AfterAll
    public static void shutdown() {
        router.shutdown();
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
