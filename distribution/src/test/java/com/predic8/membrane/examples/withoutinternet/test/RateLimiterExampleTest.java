/* Copyright 2025 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

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

