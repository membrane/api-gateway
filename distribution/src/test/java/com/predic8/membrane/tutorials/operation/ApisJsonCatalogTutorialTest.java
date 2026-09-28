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

package com.predic8.membrane.tutorials.operation;

import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.nullValue;

public class ApisJsonCatalogTutorialTest extends AbstractOperationTutorialTest {

    @Override
    protected String getTutorialYaml() {
        return "80-APIs-JSON-Catalog.yaml";
    }

    @Test
    void catalogListsEveryApi() {
        // @formatter:off
        given()
        .when()
            .get("http://localhost:2000/apis.json")
        .then()
            .statusCode(200)
            .body("name", containsString("Membrane Gateway APIs"))
            .body("apis.name", hasItems("Fruit Shop API", "Order Service", "API Catalog"));
        // @formatter:on
    }

    @Test
    void apiWithOpenAPIGetsVersionAndSwaggerUiLink() {
        // @formatter:off
        given()
        .when()
            .get("http://localhost:2000/apis.json")
        .then()
            .statusCode(200)
            .body("apis.find { it.name == 'Fruit Shop API' }.version", containsString("2.2.0"))
            .body("apis.find { it.name == 'Fruit Shop API' }.humanUrl",
                    containsString("/api-docs/ui/fruit-shop-api-v2-2-0"));
        // @formatter:on
    }

    @Test
    void apiWithoutOpenAPIUsesTheApiElement() {
        // @formatter:off
        given()
        .when()
            .get("http://localhost:2000/apis.json")
        .then()
            .statusCode(200)
            .body("apis.find { it.name == 'Order Service' }.aid", containsString("example.com:orders"))
            .body("apis.find { it.name == 'Order Service' }.description",
                    containsString("Accepts orders"))
            .body("apis.find { it.name == 'Order Service' }.humanUrl", nullValue());
        // @formatter:on
    }
}
