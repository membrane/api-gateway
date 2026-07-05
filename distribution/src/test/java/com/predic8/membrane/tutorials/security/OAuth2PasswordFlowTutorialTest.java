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

import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

public class OAuth2PasswordFlowTutorialTest extends AbstractSecurityTutorialTest {

    @Override
    protected String getTutorialYaml() {
        return "52-OAuth2-Password-Flow.yaml";
    }

    @Test
    void blocksRequestsWithoutTokenAndGrantsAccessWithUserLogin() {
        // @formatter:off
        // 1) Without a token the API rejects the request.
        given()
        .when()
            .get("http://localhost:2000")
        .then()
            .statusCode(401);

        // 2) The user logs in through the client application.
        Response login = given()
            .formParam("grant_type", "password")
            .formParam("username", "john")
            .formParam("password", "password")
            .formParam("client_id", "abc")
            .formParam("client_secret", "def")
        .when()
            .post("http://localhost:7007/oauth2/token")
        .then()
            .statusCode(200)
            .extract().response();
        String token = login.path("access_token");
        String refreshToken = login.path("refresh_token");

        // 3) The API validates the JWT and sees the user's claims.
        given()
            .header("Authorization", "Bearer " + token)
        .when()
            .get("http://localhost:2000")
        .then()
            .statusCode(200)
            .body(containsString("\"user\": \"john\""))
            .body(containsString("\"scope\": \"read write\""));

        // 4) The refresh token yields a fresh access token — no password needed —
        //    and the new token still carries the user's claims.
        String refreshedToken = given()
            .formParam("grant_type", "refresh_token")
            .formParam("refresh_token", refreshToken)
            .formParam("client_id", "abc")
            .formParam("client_secret", "def")
        .when()
            .post("http://localhost:7007/oauth2/token")
        .then()
            .statusCode(200)
            .extract().path("access_token");

        given()
            .header("Authorization", "Bearer " + refreshedToken)
        .when()
            .get("http://localhost:2000")
        .then()
            .statusCode(200)
            .body(containsString("\"user\": \"john\""))
            .body(containsString("\"scope\": \"read write\""));
        // @formatter:on
    }
}
