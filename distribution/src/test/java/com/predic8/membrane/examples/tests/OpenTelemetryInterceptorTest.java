package com.predic8.membrane.examples.tests;


import com.predic8.membrane.examples.util.DistributionExtractingTestcase;
import com.predic8.membrane.examples.util.Process2;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;

public class OpenTelemetryInterceptorTest extends DistributionExtractingTestcase {
    @Override
    protected String getExampleDirName() {
        return "opentelemetry";
    }

    @Test
    public void getResult() throws Exception {
        try (Process2 ignore = startServiceProxyScript()) {

            given()
                    .get("http://localhost:3000")
                    .then()
                    .log()
                    .body().statusCode(200);
        }
    }
}
