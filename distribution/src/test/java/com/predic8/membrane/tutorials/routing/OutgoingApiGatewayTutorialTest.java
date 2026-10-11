/* Copyright 2025 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.tutorials.routing;

import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class OutgoingApiGatewayTutorialTest extends AbstractRoutingTutorialTest {

    @Override
    protected String getTutorialYaml() {
        return "50-Outgoing-API-Gateway.yaml";
    }

    private static final String INJECTED_API_KEY = "0446F862-9EE6-46EC-9440-6B6DA0DD0ADD";

    @Test
    void onlyAllowedHeadersAreSentToTheExternalApi() {
        // @formatter:off
        given()
            .header("User-Agent", "secret")
            .header("Authorization", "secret")
        .when()
            .post("http://localhost:2000/?action=analyze")
        .then()
            .statusCode(200)
            .body("request.headers", not(hasKey("User-Agent")))
            .body("request.headers", not(hasKey("Authorization")));
        // @formatter:on
    }

    @Test
    void gatewayInjectsApiKeyWhenClientSendsNone() {
        // @formatter:off
        given()
        .when()
            .post("http://localhost:2000/?action=analyze")
        .then()
            .statusCode(200)
            .body("request.headers.X-Api-Key", contains(INJECTED_API_KEY));
        // @formatter:on
    }

    @Test
    void gatewayReplacesApiKeySentByClient() {
        // @formatter:off
        given()
            .header("X-Api-Key", "abc123")
        .when()
            .post("http://localhost:2000/?action=analyze")
        .then()
            .statusCode(200)
            .body("request.headers.X-Api-Key", contains(INJECTED_API_KEY));
        // @formatter:on
    }
}
