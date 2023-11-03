package com.predic8.membrane.examples.tests.opentelemetry;


import com.predic8.membrane.examples.util.AbstractSampleMembraneStartStopTestcase;
import com.predic8.membrane.examples.util.BufferLogger;
import com.predic8.membrane.examples.util.Process2;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class OpenTelemetryInterceptorTest extends AbstractSampleMembraneStartStopTestcase {

    @Override
    protected String getExampleDirName() {
        return "opentelemetry";
    }

    BufferLogger logger;

    @BeforeEach
    void startMembrane() throws IOException, InterruptedException {
        logger = new BufferLogger();
        process = new Process2.Builder().in(baseDir).script("service-proxy").withWatcher(logger).waitForMembrane().start();
    }

    @Test
    public void getTraceIds() {
        // @formatter:off
        given()
                .get("http://localhost:2000")
        .then().assertThat()
                .statusCode(200);
        // @formatter:on

        List<Traceparent> traceparents = Traceparent.parse(logger.toString());
        assertEquals(2, traceparents.size());
        assertEquals(traceparents.get(0).traceId, traceparents.get(1).traceId);
    }
}
