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
import com.predic8.membrane.examples.util.Process2;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

public class EnvironmentVariablesTutorialTest extends AbstractAdvancedTutorialTest{

    @Override
    protected String getTutorialYaml() {
        return "90-Environment-Variables.yaml";
    }

    @Test
    void environmentVariablesAreResolved() {
        // @formatter:off
        given()
        .when()
            .get("http://localhost:2000/")
        .then()
            .statusCode(200)
            .body(containsString("Hello from port 3000"));
        // @formatter:on
    }

    @Override
    protected Process2 startServiceProxyScript(ConsoleWatcher watch, String script) throws IOException, InterruptedException {
        Process2.Builder builder = new Process2.Builder().in(baseDir).env("PORT", "3000");

        if (watch != null)
            builder = builder.withWatcher(watch);

        return builder.script(script).waitForMembrane().parameters("-c %s".formatted(getTutorialYaml())).start();
    }

}
