/* Copyright 2023 predic8 GmbH, www.predic8.com

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
import com.predic8.membrane.test.*;
import org.junit.jupiter.api.*;

import static com.predic8.membrane.test.StringAssertions.*;
import static io.restassured.RestAssured.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;

public class AccessLogExampleTest extends DistributionExtractingTestcase {

    @Override
    protected String getExampleDirName() {
        return "logging/access";
    }

    @Test
    void console() throws Exception {
        try (var process = startServiceProxyScript()) {

            var console = new WaitableConsoleEvent(
                    process,
                    p -> p.contains("\"GET / HTTP/1.1\" 200 0 [application/json]")
            );

            given()
                .when()
                .get("http://localhost:2000")
            .then()
                .log().ifValidationFails()
                .statusCode(200);

            assertTrue(console.occurred());
        }
    }

    @Test
    void rollingFile() throws Exception {
        try (var ignore = startServiceProxyScript()) {
            given()
                    .when()
                    .get("http://localhost:2000")
                    .then()
                    .statusCode(200);
        }

        var log = readFile("access.log");
        assertThat(log, containsString("\"GET / HTTP/1.1\" 200 0 [application/json]"));
    }

    @Test
    void header() throws Exception {
        try (var ignore = startServiceProxyScript()) {
            given()
                    .when()
                    .get("http://localhost:2000")
                    .then()
                    .statusCode(200);
        }

        assertContains(
                "X-Forwarded-For: 127.0.0.1",
                readFile("access.log")
        );
    }
}
