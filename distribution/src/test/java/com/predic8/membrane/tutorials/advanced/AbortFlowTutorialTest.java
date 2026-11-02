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

package com.predic8.membrane.tutorials.advanced;

import com.predic8.membrane.examples.util.ConsoleWatcher;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AbortFlowTutorialTest extends AbstractAdvancedTutorialTest {

    @Override
    protected String getTutorialYaml() {
        return "130-Abort-Flow.yaml";
    }

    @Test
    void invalidJsonRunsAbortFlow() {
        // @formatter:off
        given()
            .contentType(JSON)
            .body("{\"a\":}")
        .when()
            .post("http://localhost:2000")
        .then()
            .statusCode(400)
            .body(equalTo("Error!"));
        // @formatter:on
    }

    @Test
    void responseFlowRunsOnlyWithoutAbort() throws InterruptedException {
        final var statusLines = new CopyOnWriteArrayList<String>();
        final var validLogged = new CountDownLatch(1);
        final ConsoleWatcher watcher = (error, line) -> {
            if (!line.contains("Status: "))
                return;
            statusLines.add(line);
            if (line.contains("Status: 200"))
                validLogged.countDown();
        };

        process.addConsoleWatcher(watcher);
        try {
            post("{\"a\":}", 400);
            post("{\"a\":1}", 200);

            // Console output arrives asynchronously but in order, so a line from the aborted
            // request would be there before the one from the valid request.
            assertTrue(validLogged.await(5, SECONDS));
            assertEquals(1, statusLines.size(), statusLines.toString());
        } finally {
            process.removeConsoleWatcher(watcher);
        }
    }

    private void post(String body, int status) {
        // @formatter:off
        given()
            .contentType(JSON)
            .body(body)
        .when()
            .post("http://localhost:2000")
        .then()
            .statusCode(status);
        // @formatter:on
    }
}
