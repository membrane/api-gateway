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

package com.predic8.membrane.tutorials.json;

import com.predic8.membrane.examples.util.ConsoleWatcher;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroovyReadJsonTutorialTest extends AbstractJsonTutorialTest {

    @Override
    protected String getTutorialYaml() {
        return "30-Groovy-Read-JSON.yaml";
    }

    @Test
    void returnsSummary() throws IOException {
        // @formatter:off
        given()
            .body(readFileFromBaseDir("animals.json"))
            .contentType(JSON)
        .when()
            .post("http://localhost:2000")
        .then()
            .statusCode(200)
            .contentType(JSON)
            .body("count", equalTo(2))
            .body("names", contains("Skye", "Molly"));
        // @formatter:on
    }

    @Test
    void logsQueryResults() throws IOException, InterruptedException {
        final var catLogged = new CountDownLatch(1);
        final var legsLogged = new CountDownLatch(1);
        final ConsoleWatcher watcher = (error, line) -> {
            if (line.contains("The cat: Molly"))
                catLogged.countDown();
            if (line.contains("Legs in total: 8"))
                legsLogged.countDown();
        };

        process.addConsoleWatcher(watcher);
        try {
            // @formatter:off
            given()
                .body(readFileFromBaseDir("animals.json"))
                .contentType(JSON)
            .when()
                .post("http://localhost:2000")
            .then()
                .statusCode(200);
            // @formatter:on

            // Console output is relayed asynchronously and can arrive after the response.
            assertTrue(catLogged.await(5, SECONDS));
            assertTrue(legsLogged.await(5, SECONDS));
        } finally {
            process.removeConsoleWatcher(watcher);
        }
    }
}
