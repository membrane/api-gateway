package com.predic8.membrane.examples.tests;


import com.predic8.membrane.examples.util.AbstractSampleMembraneStartStopTestcase;
import com.predic8.membrane.examples.util.BufferLogger;
import com.predic8.membrane.examples.util.Process2;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static com.predic8.membrane.test.AssertUtils.assertContains;
import static io.restassured.RestAssured.filters;
import static io.restassured.RestAssured.given;

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

        // Dump HTTP
        filters(new RequestLoggingFilter(), new ResponseLoggingFilter());
    }


    @Test
    public void getResult() throws Exception {
        given()
                .get("http://localhost:2000")
                .then().assertThat()
                .statusCode(200);

        assertContains("Request headers:", logger.toString());
        assertContains("traceparent", logger.toString());
    }
}
