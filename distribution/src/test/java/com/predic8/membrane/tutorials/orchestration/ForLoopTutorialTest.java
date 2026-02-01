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

package com.predic8.membrane.tutorials.orchestration;

import com.predic8.membrane.examples.util.SubstringWaitableConsoleEvent;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeoutException;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;

public class ForLoopTutorialTest extends AbstractOrchestrationTutorialTest {

    @Override
    protected String getTutorialYaml() {
        return "10-For-Loop.yaml";
    }

    @Test
    void logsEachFruitName() throws Exception {
        var apricot = new SubstringWaitableConsoleEvent(process, "Fruit: Apricot");
        var papaya = new SubstringWaitableConsoleEvent(process, "Fruit: Papaya");

        // @formatter:off
        given()
            .body(readFileFromBaseDir("fruits.json"))
            .contentType(JSON)
        .when()
            .post("http://localhost:2000")
        .then()
            .statusCode(200);
        // @formatter:on

        waitForOrFail(apricot);
        waitForOrFail(papaya);
    }

    private void waitForOrFail(SubstringWaitableConsoleEvent event) throws TimeoutException {
        event.waitFor((long) 5000);
    }
}
