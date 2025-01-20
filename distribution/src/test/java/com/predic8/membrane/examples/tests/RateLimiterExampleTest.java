/* Copyright 2015 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.examples.tests;

import com.predic8.membrane.core.http.*;
import com.predic8.membrane.examples.util.*;
import org.junit.jupiter.api.*;

import static io.restassured.RestAssured.*;
import static org.junit.jupiter.api.Assertions.*;

public class RateLimiterExampleTest extends DistributionExtractingTestcase {

    @Override
    protected String getExampleDirName() {
        return "rateLimiter";
    }

    @Test
    void clientIp() throws Exception {
        BufferLogger logger = new BufferLogger();
        try (Process2 ignored = startServiceProxyScript(logger)) {
            getAndAssert("Dummy",2000,200);
            getAndAssert("Dummy",2000,200);
            getAndAssert("Dummy",2000,200);
            getAndAssert("Dummy",2000,429);
            assertTrue(logger.contains("127.0.0.1"));
            assertTrue(logger.contains("PT30S"));
            assertTrue(logger.contains("is exceeded"));
        }
    }

    @Test
    void jsonpathExpression() throws Exception {
        BufferLogger logger = new BufferLogger();
        try (Process2 ignored = startServiceProxyScript(logger)) {
            getAndAssert("Alice",2010,200);
            getAndAssert("Alice",2010,200);
            getAndAssert("Bob",2010,200);
            getAndAssert("Alice",2010,200);
            getAndAssert("Alice",2010,429);
            getAndAssert("Bob",2010,200);
            getAndAssert("Bob",2010,200);
            getAndAssert("Bob",2010,429);
            assertTrue(logger.contains("Bob"));
            assertTrue(logger.contains("PT30S"));
            assertTrue(logger.contains("is exceeded"));
        }
    }

    private static void getAndAssert(String user, int port, int statusCode) {
        // @formatter:off
        given()
            .contentType(MimeType.APPLICATION_JSON)
            .body("""
               {
                   "user": "%s"
               }
               """.formatted(user))
            .post("http://localhost:" +port)
        .then()
            .statusCode(statusCode);
        // @formatter:on
    }
}