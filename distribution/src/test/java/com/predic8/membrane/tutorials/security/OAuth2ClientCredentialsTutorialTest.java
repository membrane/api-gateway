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
import static org.hamcrest.Matchers.equalTo;

public class OAuth2ClientCredentialsTutorialTest extends AbstractSecurityTutorialTest {

    @Override
    protected String getTutorialYaml() {
        return "51-OAuth2-Client-Credentials.yaml";
    }

    @Test
    void issuesJwtWithClaimsAndValidatesIt() {
        // @formatter:off
        // 1) Without a token the API rejects the request.
        given()
        .when()
            .get("http://localhost:2000")
        .then()
            .statusCode(401);

        // 2) Get a JWT; the granted scopes are echoed in the response.
        String token =
        given()
            .formParam("grant_type", "client_credentials")
            .formParam("client_id", "order-service")
            .formParam("client_secret", "secret")
        .when()
            .post("http://localhost:7007/oauth2/token")
        .then()
            .statusCode(200)
            .body("scope", equalTo("read write"))
        .extract().path("access_token");

        // 3) The API validates the JWT and sees the claims.
        given()
            .header("Authorization", "Bearer " + token)
        .when()
            .get("http://localhost:2000")
        .then()
            .statusCode(200)
            .body(containsString("\"client\": \"order-service\""))
            .body(containsString("\"scope\": \"read write\""))
            .body(containsString("\"aud\": \"order-api\""));

        // 4) Scopes outside the client's allowlist are rejected.
        given()
            .formParam("grant_type", "client_credentials")
            .formParam("client_id", "order-service")
            .formParam("client_secret", "secret")
            .formParam("scope", "admin")
        .when()
            .post("http://localhost:7007/oauth2/token")
        .then()
            .statusCode(400)
            .body("error", equalTo("invalid_scope"));
        // @formatter:on
    }
}
