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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.Socket;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Verifies that the hosted Membrane demo at api.predic8.de still behaves as the
 * 40-JWT-Requesting-Token.md walkthrough documents. That tutorial has no local
 * config — it drives the public demo directly — so this test needs internet, not a
 * running gateway. It exists to catch drift if the hosted demo ever changes.
 * Skipped (not failed) when api.predic8.de is unreachable, so offline runs stay green.
 */
public class RequestingTokenTutorialTest {

    private static final String TOKEN_ENDPOINT = "https://api.predic8.de/demo/oauth2/token";
    private static final String RESOURCE = "https://api.predic8.de/demo/resource";

    @BeforeAll
    static void requiresInternet() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("api.predic8.de", 443), 3000);
        } catch (Exception e) {
            assumeTrue(false, "api.predic8.de is not reachable - skipping hosted-demo test");
        }
    }

    @Test
    void requestsTokenAndCallsProtectedResource() {
        // @formatter:off
        // 1) The client credentials grant returns a bearer token (step 1 of the tutorial).
        String token =
        given()
            .auth().preemptive().basic("my-client", "my-secret")
            .formParam("grant_type", "client_credentials")
        .when()
            .post(TOKEN_ENDPOINT)
        .then()
            .statusCode(200)
            .body("token_type", equalTo("bearer"))
            .body("expires_in", equalTo(300))
            .body("access_token", notNullValue())
        .extract().path("access_token");

        // 2) The token grants access to the protected resource (step 3 of the tutorial).
        given()
            .header("Authorization", "Bearer " + token)
        .when()
            .get(RESOURCE)
        .then()
            .statusCode(200)
            .body("success", equalTo(true))
            .body("sub", equalTo("my-client"))
            .body("scope", equalTo("read write"));

        // 3) Without the token the request is rejected.
        given()
        .when()
            .get(RESOURCE)
        .then()
            .statusCode(401);
        // @formatter:on
    }
}
