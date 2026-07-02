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

public class OAuth2BasicsTutorialTest extends AbstractSecurityTutorialTest {

    @Override
    protected String getTutorialYaml() {
        return "50-OAuth2-Basics.yaml";
    }

    @Test
    void blocksWithoutTokenAndAcceptsIssuedToken() {
        // @formatter:off
        // 1) Without a token the gateway blocks the request.
        given()
        .when()
            .get("http://localhost:2000")
        .then()
            .statusCode(400);

        // 2) Get an access token (client credentials, no user involved).
        String token =
        given()
            .formParam("grant_type", "client_credentials")
            .formParam("client_id", "abc")
            .formParam("client_secret", "def")
        .when()
            .post("http://localhost:7007/oauth2/token")
        .then()
            .statusCode(200)
        .extract().path("access_token");

        // 3) With the token the gateway lets the request through.
        given()
            .header("Authorization", "Bearer " + token)
        .when()
            .get("http://localhost:2000")
        .then()
            .statusCode(200)
            .body(containsString("Protected resource accessed!"));
        // @formatter:on
    }
}
