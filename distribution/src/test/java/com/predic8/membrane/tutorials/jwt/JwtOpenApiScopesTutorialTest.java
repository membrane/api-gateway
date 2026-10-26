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

package com.predic8.membrane.tutorials.jwt;

import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

public class JwtOpenApiScopesTutorialTest extends AbstractJwtTutorialTest {

    @Override
    protected String getTutorialYaml() {
        return "30-JWT-OpenAPI-Scopes.yaml";
    }

    @Test
    void enforcesScopesFromOpenApi() {
        // 1) The API requires a token.
        // @formatter:off
        given()
        .when()
            .get("http://localhost:2000/shop/v2/products")
        .then()
            .statusCode(401);
        // @formatter:on

        // 2) A token with the scope inventory may list the products ...
        // @formatter:off
        final var token =
        given()
        .when()
            .get("http://localhost:2000/token")
        .then()
            .statusCode(200)
        .extract().asString().trim();

        given()
            .header("Authorization", "Bearer " + token)
        .when()
            .get("http://localhost:2000/shop/v2/products")
        .then()
            .statusCode(200);
        // @formatter:on

        // 3) ... but not create one, which requires the scope management.
        // @formatter:off
        given()
            .header("Authorization", "Bearer " + token)
            .contentType("application/json")
            .body("{\"name\":\"Mango\",\"price\":1.99}")
        .when()
            .post("http://localhost:2000/shop/v2/products")
        .then()
            .statusCode(403)
            .body(containsString("Caller is not in scope management"));
        // @formatter:on
    }
}
