package com.predic8.membrane.examples.withoutinternet.test;

import com.predic8.membrane.examples.util.BufferLogger;
import com.predic8.membrane.examples.util.DistributionExtractingTestcase;
import com.predic8.membrane.examples.util.Process2;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.get;
import static io.restassured.RestAssured.given;

public class RateLimiterExampleTest extends DistributionExtractingTestcase {

    @Override
    protected String getExampleDirName() {
        return "rate-limiting";
    }

    @Test
    void testGlobalRateLimitByClientIp() throws Exception {
        BufferLogger logger = new BufferLogger();
        try (Process2 ignored = startServiceProxyScript(logger)) {
            for (int i = 0; i < 10; i++) {
                get("http://localhost:2000").then().statusCode(200);
            }
            get("http://localhost:2000").then().statusCode(429);

            Assertions.assertTrue(logger.contains("127.0.0.1"));
            Assertions.assertTrue(logger.contains("PT1M"));
            Assertions.assertTrue(logger.contains("is exceeded"));
        }
    }

    @Test
    void testEndpointSpecificRateLimitByJsonUser() throws Exception {
        BufferLogger logger = new BufferLogger();
        try (Process2 ignored = startServiceProxyScript(logger)) {
            for (int i = 0; i < 3; i++) {
                given()
                        .contentType("application/json")
                        .body("{\"user\":\"Alice\"}")
                        .post("http://localhost:2010/reset-pwd")
                        .then()
                        .statusCode(200);
            }

            given()
                    .contentType("application/json")
                    .body("{\"user\":\"Alice\"}")
                    .post("http://localhost:2010/reset-pwd")
                    .then()
                    .statusCode(429);

            given()
                    .contentType("application/json")
                    .body("{\"user\":\"Bob\"}")
                    .post("http://localhost:2010/reset-pwd")
                    .then()
                    .statusCode(200);

            Assertions.assertTrue(logger.contains("Alice"));
            Assertions.assertTrue(logger.contains("PT30S"));
            Assertions.assertTrue(logger.contains("is exceeded"));
        }
    }
}

