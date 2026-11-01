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

package com.predic8.membrane.tutorials.scripting;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.fail;

public class GroovyRoutingTutorialTest extends AbstractScriptingTutorialTest {

    // A field only the response of that backend contains
    private static final Map<String, String> BACKEND_BY_MARKER = Map.of(
            "\"api\"", "apibin.io",
            "\"apis\"", "api.predic8.de",
            "postman-echo.com", "postman-echo.com");

    @Override
    protected String getTutorialYaml() {
        return "60-Groovy-Routing.yaml";
    }

    @Test
    void routesToRandomBackend() {
        for (int i = 0; i < 10; i++) {
            // @formatter:off
            final var body = given()
            .when()
                .get("http://localhost:2000")
            .then()
                .statusCode(200)
                .extract().asString();
            // @formatter:on
            backendOf(body);
        }
    }

    private static String backendOf(String body) {
        return BACKEND_BY_MARKER.entrySet().stream()
                .filter(e -> body.contains(e.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElseGet(() -> fail("Response from unknown backend: " + body));
    }
}
