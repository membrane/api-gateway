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

package com.predic8.membrane.tutorials.rate_limiting;

import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

class RateLimitByJsonFieldTutorialTest extends AbstractRateLimitingTutorialTest {

    private static final String URL = "http://localhost:2000/reset-password";

    @Override
    protected String getTutorialYaml() {
        return "30-Rate-Limit-by-JSON-Field.yaml";
    }

    @Test
    void usersAreCountedSeparately() {
        for (int i = 0; i < 3; i++) {
            resetPassword("alice", 200);
        }
        resetPassword("alice", 429);

        // @formatter:off
        given()
            .contentType("application/json")
            .body("{\"user\":\"bob\"}")
        .when()
            .post(URL)
        .then()
            .statusCode(200)
            .body(equalTo("Password reset link sent"));

        given()
            .contentType("application/json")
            .body("{")
        .when()
            .post(URL)
        .then()
            .statusCode(400);
        // @formatter:on
    }

    private static void resetPassword(String user, int expectedStatus) {
        // @formatter:off
        given()
            .contentType("application/json")
            .body("{\"user\":\"%s\"}".formatted(user))
        .when()
            .post(URL)
        .then()
            .statusCode(expectedStatus);
        // @formatter:on
    }
}
