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

package com.predic8.membrane.tutorials.cors;

import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class CorsRestrictedTutorialTest extends AbstractCorsTutorialTest {

    @Override
    protected String getTutorialYaml() {
        return "20-CORS-Restricted.yaml";
    }

    @Test
    void allowedPreflightIsAnswered() {
        // @formatter:off
        given()
            .header("Origin", "https://example.com")
            .header("Access-Control-Request-Method", "POST")
            .header("Access-Control-Request-Headers", "X-Foo")
        .when()
            .options("http://localhost:2000")
        .then()
            .statusCode(204)
            .header("Access-Control-Allow-Origin", "https://example.com")
            .header("Access-Control-Allow-Methods", "POST")
            .header("Access-Control-Allow-Headers", containsString("x-foo"));
        // @formatter:on
    }

    @Test
    void preflightFromFilePageIsAnswered() {
        // @formatter:off
        given()
            .header("Origin", "null")
            .header("Access-Control-Request-Method", "POST")
            .header("Access-Control-Request-Headers", "content-type,x-foo")
        .when()
            .options("http://localhost:2000")
        .then()
            .statusCode(204)
            .header("Access-Control-Allow-Origin", "null");
        // @formatter:on
    }

    @Test
    void headerNotAllowed() {
        // @formatter:off
        given()
            .header("Origin", "https://example.com")
            .header("Access-Control-Request-Method", "POST")
            .header("Access-Control-Request-Headers", "X-Bar")
        .when()
            .options("http://localhost:2000")
        .then()
            .statusCode(403)
            .body(containsString("headers-not-allowed"));
        // @formatter:on
    }

    @Test
    void methodNotAllowed() {
        // @formatter:off
        given()
            .header("Origin", "null")
            .header("Access-Control-Request-Method", "PUT")
        .when()
            .options("http://localhost:2000")
        .then()
            .statusCode(403)
            .body(containsString("method-not-allowed"));
        // @formatter:on
    }

    @Test
    void requestFromOtherOriginReachesApiWithoutAllowOrigin() {
        // @formatter:off
        given()
            .header("Origin", "https://evil.example.org")
        .when()
            .get("http://localhost:2000")
        .then()
            .statusCode(200)
            .header("Access-Control-Allow-Origin", nullValue())
            .body(equalTo("Hello from the CORS API!"));
        // @formatter:on
    }
}
