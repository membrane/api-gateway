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

import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.interceptor.misc.ReturnInterceptor;
import com.predic8.membrane.core.util.URIFactory;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.JsonSchema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.*;

import static com.predic8.membrane.core.openapi.serviceproxy.OpenAPISpec.YesNoOpenAPIOption.YES;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class OpenAPI31ReferencesTest {

    static APIProxy api;

    @BeforeAll
    public static void setUp() throws Exception {
        Router router = new HttpRouter();
        router.setUriFactory(new URIFactory());

        OpenAPISpec spec = new OpenAPISpec();
        spec.location = "src/test/resources/openapi/specs/oas31/request-reference.yaml";
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

    @Test
    void navigateThroughReferencedPartsOfDocument() {
        OpenAPI openAPI = api.apiRecords.get("demo-v1-0-0").getApi();
        PathItem pathItem = openAPI.getPaths().get("/users");
        assertNotNull(pathItem);
        Operation operation = pathItem.readOperations().get(0);
        assertNotNull(operation);
        assertEquals("Demo", operation.getDescription());
        RequestBody requestBody = operation.getRequestBody();
        assertNotNull(requestBody);
        Content content = requestBody.getContent();
        assertNotNull(content);
        JsonSchema schema = (JsonSchema) content.get("application/json").getSchema();
        assertNotNull(schema);
        assertEquals("email", schema.getRequired().get(0));
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
            .body(containsString("is not a valid email"));
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