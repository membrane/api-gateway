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

package com.predic8.membrane.tutorials.loadbalancing;

import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class StickySessionsTutorialTest extends AbstractLoadbalancingTutorialTest {

    @Override
    protected String getTutorialYaml() {
        return "30-Sticky-Sessions.yaml";
    }

    @Test
    void sessionStaysOnItsNode() {
        final var alice = callAs("alice");
        final var bob = callAs("bob");
        assertNotEquals(alice, bob);
        assertEquals(alice, callAs("alice"));
    }

    private static String callAs(String sessionId) {
        final var nodes = new HashSet<String>();
        for (var i = 0; i < 5; i++) {
            // @formatter:off
            nodes.add(given()
                    .header("X-Session-Id", sessionId)
                .when()
                    .get("http://localhost:2000")
                .then()
                    .statusCode(200)
                    .extract().asString());
            // @formatter:on
        }
        assertEquals(1, nodes.size(), "Session %s was spread over %s".formatted(sessionId, nodes));
        return nodes.iterator().next();
    }
}
