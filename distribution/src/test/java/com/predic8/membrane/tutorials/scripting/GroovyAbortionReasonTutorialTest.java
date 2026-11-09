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

import java.util.concurrent.CountDownLatch;

import static io.restassured.RestAssured.given;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroovyAbortionReasonTutorialTest extends AbstractScriptingTutorialTest {

    @Override
    protected String getTutorialYaml() {
        return "95-Groovy-Abortion-Reason.yaml";
    }

    @Test
    void abortFlowPrintsAbortionReason() throws InterruptedException {
        final var abortionReasonLogged = new CountDownLatch(1);
        final ConsoleWatcher watcher = (error, line) -> {
            if (line.contains("Aborted: Something went wrong"))
                abortionReasonLogged.countDown();
        };

        process.addConsoleWatcher(watcher);
        try {
            // @formatter:off
            given()
            .when()
                .get("http://localhost:2000")
            .then()
                .statusCode(500);
            // @formatter:on

            assertTrue(abortionReasonLogged.await(5, SECONDS));
        } finally {
            process.removeConsoleWatcher(watcher);
        }
    }
}
