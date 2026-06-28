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
 * Verifies tutorial step 40-OpenTelemetry.yaml: a three-hop chain
 * (Gateway → Microservice A → Microservice B) with an explicit Jaeger
 * OTLP/gRPC endpoint. Requests succeed even when Jaeger is not running —
 * the exporter silently drops unreachable spans. The openTelemetry plugin
 * writes traceId and spanId into the SLF4J MDC for every request, and each
 * API logs the propagated traceparent header.
 */
public class OpenTelemetryTutorialTest extends AbstractOperationTutorialTest {

    @Override
    protected String getTutorialYaml() {
        return "40-OpenTelemetry.yaml";
    }

    @Test
    void requestSucceeds() {
        given()
        .when()
            .get("http://localhost:2000")
        .then()
            .statusCode(200);
    }

    @Test
    void traceIdIsWrittenToMdc() throws Exception {
        var traceInMdc = new SubstringWaitableConsoleEvent(process, "traceId=");

        given()
        .when()
            .get("http://localhost:2000")
        .then()
            .statusCode(200);

        traceInMdc.waitFor(5000);
    }

    @Test
    void traceparentIsPropagatedToBackend() throws Exception {
        var traceparent = new SubstringWaitableConsoleEvent(process, "traceparent: 00-");

        given()
        .when()
            .get("http://localhost:2000")
        .then()
            .statusCode(200);

        traceparent.waitFor(5000);
    }
}
