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

package com.predic8.membrane.tutorials.operation;

import com.predic8.membrane.examples.util.SubstringWaitableConsoleEvent;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;

/**
 * Verifies tutorial step 10-Correlation-Id.yaml: when the header is absent an id is
 * generated from the 'default' expression; when it is present the value is reused.
 * Both end up in the line the 'log' plugin prints to the console.
 */
public class CorrelationIdTutorialTest extends AbstractOperationTutorialTest {

    @Override
    protected String getTutorialYaml() {
        return "10-Correlation-Id.yaml";
    }

    @Test
    void generatesIdWhenHeaderIsAbsent() throws Exception {
        var generated = new SubstringWaitableConsoleEvent(process, "Correlation id: req-");

        given()
        .when()
            .get("http://localhost:2000")
        .then()
            .statusCode(200);

        generated.waitFor(5000);
    }

    @Test
    void reusesIncomingId() throws Exception {
        var reused = new SubstringWaitableConsoleEvent(process, "Correlation id: 1234-5678");

        given()
            .header("X-Trace-Id", "1234-5678")
        .when()
            .get("http://localhost:2000")
        .then()
            .statusCode(200);

        reused.waitFor(5000);
    }
}
