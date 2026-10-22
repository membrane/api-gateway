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

package com.predic8.membrane.tutorials.openapi;

import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;

public class OpenApiExtensionsTutorialTest extends AbstractOpenAPITutorialTest {

    @Override
    protected String getTutorialYaml() {
        return "80-OpenAPI-Extensions.apis.yaml";
    }

    @Test
    void idFromXMembraneId() {
        // @formatter:off
        given()
        .when()
            .get("http://localhost:2000/api-docs")
        .then()
            .statusCode(200)
            .body("'shop-v2-0'.title", equalTo("Minimal Shop API"));

        given()
        .when()
            .get("http://localhost:2000/api-docs/shop-v2-0")
        .then()
            .statusCode(200)
            .body(containsString("Minimal Shop API"));
        // @formatter:on
    }

    @Test
    void validationFromXMembraneValidation() {
        // @formatter:off
        given()
            .contentType("application/json")
            .body("""
            { "name": "Figs", "price": -2.7 }
            """)
        .when()
            .post("http://localhost:2000/ext-shop/products")
        .then()
            .statusCode(400)
            .body("title", equalTo("OpenAPI message validation failed"));
        // @formatter:on
    }
}
