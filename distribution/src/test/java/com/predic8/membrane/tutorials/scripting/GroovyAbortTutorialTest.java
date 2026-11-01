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

import com.predic8.membrane.examples.util.ConsoleWatcher;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroovyAbortTutorialTest extends AbstractScriptingTutorialTest {

    private static final List<String> FLOW_LOGS = List.of("Request received", "Handling error", "Response sent");

    @Override
    protected String getTutorialYaml() {
        return "90-Groovy-Abort.yaml";
    }

    @Test
    void missingTokenRunsAbortFlow() {
        // @formatter:off
        given()
        .when()
            .get("http://localhost:2000")
        .then()
            .statusCode(401)
            .contentType(JSON)
            .body("error", equalTo("Missing X-Token header"));
        // @formatter:on
    }

    @Test
    void abortAndResponseFlowRunExclusively() throws InterruptedException {
        final var logged = new CopyOnWriteArrayList<String>();
        final var responseSent = new CountDownLatch(1);
        final ConsoleWatcher watcher = (error, line) -> {
            FLOW_LOGS.stream().filter(line::contains).findFirst().ifPresent(logged::add);
            if (line.contains("Response sent"))
                responseSent.countDown();
        };

        process.addConsoleWatcher(watcher);
        try {
            // @formatter:off
            given()
            .when()
                .get("http://localhost:2000")
            .then()
                .statusCode(401);

            given()
                .header("X-Token", "abc")
            .when()
                .get("http://localhost:2000")
            .then()
                .statusCode(200);
            // @formatter:on

            // Console output arrives asynchronously but in order.
            assertTrue(responseSent.await(5, SECONDS));
            assertEquals(List.of("Request received", "Handling error", "Request received", "Response sent"), logged);
        } finally {
            process.removeConsoleWatcher(watcher);
        }
    }
}
