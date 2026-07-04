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
import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.equalTo;

public class SqlInjectionProtectionTutorialTest extends AbstractSecurityTutorialTest {

    @Override
    protected String getTutorialYaml() {
        return "100-SQL-Injection-Protection.yaml";
    }

    @Test
    void blocksSqlInjectionAttempts() {
        assertBlocked("{\"q\":\"SELECT pw FROM information_schema.tables\"}");
        assertBlocked("{\"q\":\"1 OR sleep(5)\"}");
    }

    private void assertBlocked(String payload) {
        // @formatter:off
        given()
            .contentType(JSON)
            .body(payload)
        .when()
            .post("http://localhost:2000")
        .then()
            .statusCode(400)
            .body("title", equalTo("Request blocked by SQL injection protection"));
        // @formatter:on
    }

    @Test
    void allowsCleanRequest() {
        // @formatter:off
        given()
            .contentType(JSON)
            .body("{\"q\":\"laptop\"}")
        .when()
            .post("http://localhost:2000")
        .then()
            .statusCode(200);
        // @formatter:on
    }
}
