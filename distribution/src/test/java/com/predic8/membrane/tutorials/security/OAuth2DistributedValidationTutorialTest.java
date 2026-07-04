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

import com.predic8.membrane.examples.util.DistributionExtractingTestcase;
import com.predic8.membrane.examples.util.Process2;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

/**
 * Two-instance tutorial (54a + 54b): 54a issues signed JWTs and publishes its public
 * keys at a JWKS endpoint, 54b validates those tokens by fetching the keys over HTTP.
 * The issuer must be up before the validator starts, because jwtAuth resolves the
 * JWKS at startup — hence starting the issuer first with waitForMembrane().
 */
public class OAuth2DistributedValidationTutorialTest extends DistributionExtractingTestcase {

    @Override
    protected String getExampleDirName() {
        return "../tutorials/security";
    }

    private Process2 issuer;
    private Process2 validator;

    @BeforeEach
    void startInstances() throws Exception {
        issuer = new Process2.Builder().in(baseDir).script("membrane")
                .withParameters("-c 54a-OAuth2-Distributed-Issuer.yaml").waitForMembrane().start();
        validator = new Process2.Builder().in(baseDir).script("membrane")
                .withParameters("-c 54b-OAuth2-Distributed-Validation.yaml").waitForMembrane().start();
    }

    @AfterEach
    void stopInstances() {
        if (validator != null)
            validator.killScript();
        if (issuer != null)
            issuer.killScript();
    }

    @Test
    void issuesJwtAndValidatesViaJwks() {
        // @formatter:off
        // 1) The protected API rejects requests without a token.
        given()
        .when()
            .get("http://localhost:2000")
        .then()
            .statusCode(401);

        // 2) Get a signed JWT access token from the issuer (password grant).
        String token =
        given()
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

        // 3) The validator accepts the token after fetching the issuer's public keys.
        given()
            .header("Authorization", "Bearer " + token)
        .when()
            .get("http://localhost:2000")
        .then()
            .statusCode(200)
            .body(containsString("Hello, john!"));
        // @formatter:on
    }
}
