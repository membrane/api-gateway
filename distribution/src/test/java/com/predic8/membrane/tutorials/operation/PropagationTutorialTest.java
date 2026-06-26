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
 * Verifies tutorial step 20-Propagation.yaml: the globally declared correlationId
 * is forwarded from the edge API (2000) to the internal API (2001), so the id the
 * internal API logs is the same one the edge API generated or received.
 */
public class PropagationTutorialTest extends AbstractOperationTutorialTest {

    @Override
    protected String getTutorialYaml() {
        return "20-Propagation.yaml";
    }

    @Test
    void generatedIdReachesInternalApi() throws Exception {
        // The internal API (port 2001) logging a generated 'req-' id in its MDC proves
        // the id propagated there. The id appears automatically as {api=..., correlationId=...}.
        var internal = new SubstringWaitableConsoleEvent(process, "0.0.0.0:2001, correlationId=req-");

        given()
        .when()
            .get("http://localhost:2000")
        .then()
            .statusCode(200);

        internal.waitFor(5000);
    }

    @Test
    void incomingIdReachesBothApis() throws Exception {
        var edge = new SubstringWaitableConsoleEvent(process, "0.0.0.0:2000, correlationId=order-4711");
        var internal = new SubstringWaitableConsoleEvent(process, "0.0.0.0:2001, correlationId=order-4711");

        given()
            .header("X-Correlation-Id", "order-4711")
        .when()
            .get("http://localhost:2000")
        .then()
            .statusCode(200);

        edge.waitFor(5000);
        internal.waitFor(5000);
    }
}
