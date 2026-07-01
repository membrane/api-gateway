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
import static org.hamcrest.Matchers.containsString;

public class OAuth2PasswordFlowTutorialTest extends AbstractSecurityTutorialTest {

    @Override
    protected String getTutorialYaml() {
        return "51-OAuth2-Password-Flow.yaml";
    }

    @Test
    void blocksRequestsWithoutTokenAndGrantsAccessWithValidToken() {
        // @formatter:off
        given()
        .when()
            .get("http://localhost:2000")
        .then()
            .statusCode(400);

        String token = given()
            .formParam("grant_type", "password")
            .formParam("username", "john")
            .formParam("password", "password")
            .formParam("client_id", "abc")
            .formParam("client_secret", "def")
        .when()
            .post("http://localhost:7007/oauth2/token")
        .then()
            .statusCode(200)
            .extract().path("access_token");

        given()
            .header("Authorization", "Bearer " + token)
        .when()
            .get("http://localhost:2000")
        .then()
            .statusCode(200)
            .body(containsString("Secret resource accessed!"));
        // @formatter:on
    }
}
