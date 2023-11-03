package com.predic8.membrane.examples.tests;


import com.predic8.membrane.examples.util.AbstractSampleMembraneStartStopTestcase;
import com.predic8.membrane.examples.util.BufferLogger;
import com.predic8.membrane.examples.util.Process2;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.regex.Matcher;

import static io.restassured.RestAssured.given;
import static java.util.regex.Pattern.compile;
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
    public void getResult() throws Exception {
        given()
                .get("http://localhost:2000")
                .then().assertThat()
                .statusCode(200);

        Matcher m = compile("traceparent:\\s+\\S+").matcher(logger.toString());
        ArrayList<String> traces = new ArrayList<>();
        while (m.find()) {
            // split string at - and get traceId
            traces.add(m.group(0).trim().split("-")[1]);
        }

        // check if traceparent appears twice in the header
        assertEquals(2, traces.size());

        // check if traceId matches:
        assertEquals(traces.get(0), traces.get(1));

    }
}
