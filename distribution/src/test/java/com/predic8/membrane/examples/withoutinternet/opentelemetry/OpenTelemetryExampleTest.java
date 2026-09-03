/* Copyright 2024 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.examples.withoutinternet.opentelemetry;


import com.predic8.membrane.examples.util.AbstractSampleMembraneStartStopTestcase;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.predic8.membrane.examples.withoutinternet.opentelemetry.Traceparent.parse;
import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OpenTelemetryExampleTest extends AbstractSampleMembraneStartStopTestcase {

    @Override
    protected String getExampleDirName() {
        return "monitoring-tracing/opentelemetry";
    }

    @Test
    void getTraceIds() {
        // @formatter:off
        given()
            .get("http://localhost:2000")
        .then().assertThat()
            .statusCode(200);
        // @formatter:on

        List<Traceparent> traceparents = parse(logger.toString());
        assertEquals(4, traceparents.size());
        assertTrue(traceparents.get(0).sameTraceId(traceparents.get(1)));
    }
}
