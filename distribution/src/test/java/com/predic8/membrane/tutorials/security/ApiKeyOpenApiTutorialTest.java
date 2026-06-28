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

package com.predic8.membrane.tutorials.security;

import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;

public class ApiKeyOpenApiTutorialTest extends AbstractSecurityTutorialTest {

    @Override
    protected String getTutorialYaml() {
        return "120-API-Key-OpenAPI.yaml";
    }

    @Test
    void enforcesOpenApiSecurityRequirements() {
        // @formatter:off
        given()
        .when()
            .get("http://localhost:2000/shop/v2/products")
        .then()
            .statusCode(401);

        given()
            .header("X-Api-Key", "111")
            .contentType("application/json")
            .body("{}")
        .when()
            .post("http://localhost:2000/shop/v2/products")
        .then()
            .statusCode(403);

        given()
            .header("X-Api-Key", "222")
            .contentType("application/json")
            .body("{\"name\":\"Mango\",\"price\":1.99}")
        .when()
            .post("http://localhost:2000/shop/v2/products")
        .then()
            .statusCode(201);
        // @formatter:on
    }
}
