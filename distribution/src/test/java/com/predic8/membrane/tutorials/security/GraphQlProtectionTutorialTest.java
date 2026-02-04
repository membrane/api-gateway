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

import org.junit.jupiter.api.*;

import static io.restassured.RestAssured.*;
import static io.restassured.http.ContentType.*;
import static org.hamcrest.Matchers.*;

public class GraphQlProtectionTutorialTest extends AbstractSecurityTutorialTest {

    @Override
    protected String getTutorialYaml() {
        return "95-GraphQL-Protection.yaml";
    }

    @Test
    void allowsValidQueryAndRejectsExcessiveRecursion() {
        // @formatter:off
        given()
            .contentType(JSON)
            .body("{\"query\":\"{products{id}}\"}")
        .when()
            .post("http://localhost:2000")
        .then()
            .statusCode(200)
            .body(containsString("products"));

        given()
            .contentType(JSON)
            .body("{\"query\":\"{products{vendor{products{vendor{products{id}}}}}}\"}")
        .when()
            .post("http://localhost:2000")
        .then()
            .statusCode(400)
            .body("title", equalTo("GraphQL protection violation"));
        // @formatter:on
    }

    @Test
    void rejectsDisallowedMutation_updateCategory() {
        // Try 3
        // @formatter:off
        given()
            .contentType(JSON)
            .body("{\"query\":\"mutation { updateCategory(id:6,name: \\\"Dry Fruits\\\"){id}}\"}")
        .when()
            .post("http://localhost:2000")
        .then()
            .statusCode(400)
            .body("title", equalTo("GraphQL protection violation"));
        // @formatter:on
    }

    @Test
    void rejectsIntrospection() {
        // Try 4
        // @formatter:off
        given()
            .contentType(JSON)
            .body("{\"query\":\"{ __schema{mutationType {fields{name}}}}\"}")
        .when()
            .post("http://localhost:2000")
        .then()
            .statusCode(400)
            .body("title", equalTo("GraphQL protection violation"));
        // @formatter:on
    }
}
