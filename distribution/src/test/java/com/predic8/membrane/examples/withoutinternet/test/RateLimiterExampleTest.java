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

import com.predic8.membrane.examples.util.*;
import org.junit.jupiter.api.*;

import static io.restassured.RestAssured.*;
import static org.junit.jupiter.api.Assertions.*;

public class RateLimiterExampleTest extends DistributionExtractingTestcase {

    @Override
    protected String getExampleDirName() {
        return "rate-limiting";
    }

    @Test
    void globalRateLimitByClientIp() throws Exception {
        BufferLogger logger = new BufferLogger();
        try (Process2 ignored = startServiceProxyScript(logger)) {
            for (int i = 0; i < 10; i++) {
                get("http://localhost:2000").then().statusCode(200);
            }
            get("http://localhost:2000").then().statusCode(429);

            assertTrue(logger.contains("127.0.0.1"));
            assertTrue(logger.contains("PT1M"));
            assertTrue(logger.contains("is exceeded"));
        }
    }

    @Test
    void endpointSpecificRateLimitByJsonUser() throws Exception {
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

            assertTrue(logger.contains("Alice"));
            assertTrue(logger.contains("PT30S"));
            assertTrue(logger.contains("is exceeded"));
        }
    }
}

