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

package com.predic8.membrane.tutorials.advanced;

import com.predic8.membrane.examples.util.ConsoleWatcher;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertTrue;


public class ScriptingGroovyTutorialTest extends AbstractAdvancedTutorialTest {

    @Override
    protected String getTutorialYaml() {
        return "80-Scripting-Groovy.yaml";
    }

    @Test
    void groovyEndpointLogs() throws InterruptedException {
        CountDownLatch requestFlowLogged = new CountDownLatch(1);
        CountDownLatch responseFlowLogged = new CountDownLatch(1);
        ConsoleWatcher watcher = (error, line) -> {
            if (line.contains("I'm executed in the REQUEST flow"))
                requestFlowLogged.countDown();
            if (line.contains("I'm executed in the RESPONSE flow"))
                responseFlowLogged.countDown();
        };

        process.addConsoleWatcher(watcher);
        try {
            // @formatter:off
            given()
            .when()
                .get("http://localhost:2000/groovy")
            .then()
                .statusCode(200);
            // @formatter:on

            // The gateway runs as a separate process; its console output is relayed
            // asynchronously by a background reader thread, so it can still be in flight after
            // the HTTP response has already come back.
            assertTrue(requestFlowLogged.await(5, TimeUnit.SECONDS));
            assertTrue(responseFlowLogged.await(5, TimeUnit.SECONDS));
        } finally {
            process.removeConsoleWatcher(watcher);
        }
    }

    @Test
    void randomEndpoint() {
        // @formatter:off
        given()
        .when()
            .get("http://localhost:2000/random")
        .then()
            .body(notNullValue());
        // @formatter:on
    }

    @Test
    void groovyCustomResponse() {
        // @formatter:off
        given()
        .when()
            .get("http://localhost:2000/response")
        .then()
            .statusCode(200)
            .contentType("application/json")
            .header("X-Foo", "bar")
            .body("success", equalTo(true));
        // @formatter:on
    }

}
