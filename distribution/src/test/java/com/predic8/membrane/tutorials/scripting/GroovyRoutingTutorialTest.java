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

import java.util.HashSet;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.in;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GroovyRoutingTutorialTest extends AbstractScriptingTutorialTest {

    @Override
    protected String getTutorialYaml() {
        return "60-Groovy-Routing.yaml";
    }

    @Test
    void routesToRandomBackend() {
        final var backends = new HashSet<String>();
        for (int i = 0; i < 20; i++) {
            // @formatter:off
            backends.add(given()
            .when()
                .get("http://localhost:2000")
            .then()
                .statusCode(200)
                .body(in(new String[]{"Backend A", "Backend B", "Backend C"}))
                .extract().asString());
            // @formatter:on
        }
        // All 20 calls hitting the same backend has a probability of 3 * (1/3)^20.
        assertTrue(backends.size() > 1, "Always routed to " + backends);
    }
}
