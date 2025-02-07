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
package com.predic8.membrane.examples.tests;

import com.predic8.membrane.examples.util.AbstractSampleMembraneStartStopTestcase;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static com.predic8.membrane.core.http.Header.AUTHORIZATION;
import static io.restassured.RestAssured.given;
import static java.util.Base64.getUrlDecoder;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ApiKeyToJwtTest extends AbstractSampleMembraneStartStopTestcase {

    public static final String X_API_KEY = "X-Api-Key";

    private static final String TOKEN = "eyJhbGciOiJSUzI1NiIsImtpZCI6Im1lbWJyYW5lIn0.eyJzdWIiOiJ1c2VyQGV4YW1wbGUuY29tIiwiYXVkIjoib3JkZXIiLCJzY29wZSI6IlthY2NvdW50aW5nLCBmaW5hbmNlXSIsImlhdCI6MTczODgzNzE5NCwiZXhwIjozODg2MzIwODQxfQ.XvG7hogaX3mKVqwNL1zR8DQl_6Us5drHvfu_LKv_SousCKrjmBAIOM9hx_80TCUDeeyL2NvG82AC0MRHyBOy0f42srkqyphxGkJnNsp-LusvI8rUWvrSvSC6vARA9e3IrhubRNHr8eATPBRmCUd7YTKsAfGe_Jpm4Ytp1YNxyj3FFvtuw6BiPpq81ZIXky9BZT7Z38VZDULqjBduVE9DAYwJE11H1iEjLSsEaOAbdJon4Gsq90fD9Ab34CZ7y0DKwp0bSTZhHUJDmQF1Zh-1Tz0FbDzLJ55WcCoKbOkkiiTe4bWSBlsFL0Ez5mCAxD9Yld-GmdZ7hbDhVG1cvnSPbw";

    @Override
    protected String getExampleDirName() {
        return "security/jwt/apikey-to-jwt-conversion";
    }

    //@formatter:off
    @Test
    void invalidKey() {
        given()
            .header(X_API_KEY, "98765")
        .when()
            .get("http://localhost:2000")
        .then().assertThat()
                .body(containsString("The provided API key is invalid."))
            .statusCode(403);
    }

    @Test
    void validKey() {
        Response response =
                given()
                        .header(X_API_KEY, "123456789")
                .when()
                    .get("http://localhost:2000")
                .then()
                    .statusCode(200)
                    .extract().response();

        String[] parts = response.getBody().asString().trim().split("\\.");
        assertTrue(parts.length >= 2);
        assertTrue(new String(getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8)
                .contains("scope\":\"[accounting, finance]")
        );
    }

    @Test
    void invalidJwt() {
        given()
            .header(AUTHORIZATION, "Bearer " + TOKEN.replace("a", "d"))
        .when()
            .get("http://localhost:2001")
        .then().assertThat()
            .body(containsString("JWT validation failed")).statusCode(400);
    }

    @Test
    void validJwt() {
        given()
            .header(AUTHORIZATION, "Bearer " + TOKEN)
        .when()
            .get("http://localhost:2001")
        .then().assertThat()
            .body(
                    containsString("You accessed protected content!"),
                    containsString("JWT Scopes: [accounting, finance]")
            ).statusCode(200);
    }
    //@formatter:on
}
