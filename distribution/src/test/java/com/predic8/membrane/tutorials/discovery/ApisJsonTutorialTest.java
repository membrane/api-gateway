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

package com.predic8.membrane.tutorials.discovery;

import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class ApisJsonTutorialTest extends AbstractDiscoveryTutorialTest {

    @Override
    protected String getTutorialYaml() {
        return "10-APIs-JSON.yaml";
    }

    @Test
    void listsOnlyPublicApis() {
        // @formatter:off
        given()
        .when()
            .get("http://localhost:2000/.well-known/apis.json")
        .then()
            .statusCode(200)
            .body("name", equalTo("Example Shop"))
            .body("apis.name", contains("Products API", "Greeting API"))
            .body("aid", equalTo("localhost:shop"))
            .body("apis[0].aid", equalTo("localhost:products-api-v1-0"))
            .body("apis[1].aid", equalTo("localhost:greeting"))
            .body("apis[1].baseURL", equalTo("http://localhost:2000/greeting"));
        // @formatter:on
    }

    @Test
    void linksTheOpenApiDocument() {
        // @formatter:off
        given()
        .when()
            .get("http://localhost:2000/.well-known/apis.json")
        .then()
            .statusCode(200)
            .body("apis[0].humanURL", equalTo("http://localhost:2000/api-docs/ui/products-api-v1-0"))
            .body("apis[0].properties.find { it.type == 'OpenAPI' }.url", equalTo("http://localhost:2000/api-docs/products-api-v1-0"));

        given()
        .when()
            .get("http://localhost:2000/api-docs/products-api-v1-0")
        .then()
            .statusCode(200)
            .body(containsString("Products API"));
        // @formatter:on
    }

    @Test
    void publishesTheConfiguredCatalogMetadata() {
        // @formatter:off
        given()
        .when()
            .get("http://localhost:2000/.well-known/apis.json")
        .then()
            .statusCode(200)
            .body("type", equalTo("Collection"))
            .body("maintainers[0].FN", equalTo("API Team"))
            .body("common.type", contains("Signup", "TermsOfService"))
            .body("apis[0].properties.type", contains("OpenAPI", "Documentation", "Pricing"))
            .body("apis[0].contact[0].FN", equalTo("Shop Team"))
            .body("apis[0].contact[0].email", equalTo("shop@example.com"));
        // @formatter:on
    }

    @Test
    void greetingApiAnswers() {
        // @formatter:off
        given()
        .when()
            .get("http://localhost:2000/greeting")
        .then()
            .statusCode(200)
            .body("greeting", equalTo("Hello!"));
        // @formatter:on
    }
}
