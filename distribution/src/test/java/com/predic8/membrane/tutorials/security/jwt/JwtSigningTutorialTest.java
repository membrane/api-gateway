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

package com.predic8.membrane.tutorials.security.jwt;

import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

public class JwtSigningTutorialTest extends AbstractSecurityJwtTutorialTest {

    @Override
    protected String getTutorialYaml() {
        return "41-JWT-Signing.yaml";
    }

    @Test
    void issuesTokenAndProtectsResource() {
        // 1) Wrong credentials are rejected by HTTP Basic authentication.
        // @formatter:off
        given()
            .auth().preemptive().basic("alice", "wrong")
        .when()
            .post("http://localhost:2000/token")
        .then()
            .statusCode(401);
        // @formatter:on

        // 2) The protected resource requires a token.
        // @formatter:off
        given()
        .when()
            .get("http://localhost:2000/resource")
        .then()
            .statusCode(401);
        // @formatter:on

        // 3) The authenticated user gets a token whose "sub" is their own username.
        // @formatter:off
        String accessToken =
        given()
            .auth().preemptive().basic("alice", "alice-secret")
        .when()
            .post("http://localhost:2000/token")
        .then()
            .statusCode(200)
            .body("token_type", equalTo("bearer"))
            .body("expires_in", equalTo(300))
            .body("access_token", notNullValue())
        .extract().path("access_token");

        given()
            .header("Authorization", "Bearer " + accessToken)
        .when()
            .get("http://localhost:2000/resource")
        .then()
            .statusCode(200)
            .body("client", equalTo("alice"))
            .body("scope", equalTo("read write"));
        // @formatter:on
    }
}
