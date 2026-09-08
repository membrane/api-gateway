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

package com.predic8.membrane.tutorials.getting_started;

import com.predic8.membrane.examples.util.BufferLogger;
import com.predic8.membrane.examples.util.DistributionExtractingTestcase;
import com.predic8.membrane.examples.util.Process2;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Console output must be captured to see the reload log message, so this extends
 * {@link DistributionExtractingTestcase} directly instead of {@code AbstractGettingStartedTutorialTest}
 * (which starts Membrane without a watcher).
 */
public class HotReloadTutorialTest extends DistributionExtractingTestcase {

    private static final String YAML = "05-Hot-Reload.yaml";

    protected Process2 process;
    private final BufferLogger logger = new BufferLogger();

    @Override
    protected String getExampleDirName() {
        return "../tutorials/getting-started";
    }

    @Override
    protected String getParameters() {
        return "-c " + YAML;
    }

    @BeforeEach
    void startGateway() throws IOException, InterruptedException {
        process = startServiceProxyScript(logger);
    }

    @AfterEach
    void stopGateway() {
        if (process != null)
            process.killScript();
    }

    @Test
    void reloadsConfigurationWhenFileChanges() throws IOException, InterruptedException {
        // 2.) Call the API: the response comes from api.predic8.de
        // @formatter:off
        given()
        .when()
            .get("http://localhost:2000")
        .then()
            .statusCode(200)
            .body(containsString("Shop API Showcase"));
        // @formatter:on

        // 3.) While Membrane is running, change the target url and save the file.
        replaceInFile2(YAML, "url: https://api.predic8.de", "url: https://apibin.io/");

        // The hot deployment thread polls once a second; give it a few rounds to pick up the change.
        String body = null;
        for (int i = 0; i < 10 && (body == null || !body.contains("apibin")); i++) {
            Thread.sleep(1000);
            try {
                body = given().when().get("http://localhost:2000").then().extract().body().asString();
            } catch (Exception ignoredWhileRouterRestarts) {
                // the router briefly stops and restarts its listener while reloading
            }
        }

        // 4.) Call the API again: the response now comes from apibin.io, no restart needed.
        assertTrue(body != null && body.contains("apibin"), "expected the reloaded config to route to apibin.io");
        assertTrue(logger.contains("Configuration reloaded."), "expected the reload to be logged");
    }
}
