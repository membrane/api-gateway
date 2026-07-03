/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.examples.withinternet.test;

import com.predic8.membrane.examples.util.AbstractSampleMembraneStartStopTestcase;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConfigurationExampleTest extends AbstractSampleMembraneStartStopTestcase {

    @Override
    protected String getExampleDirName() {
        return "configuration";
    }

    @Test
    void consoleLogs() {
        synchronized (System.out) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PrintStream original = System.out;
            System.setOut(new PrintStream(out));

            try {
                // @formatter:off
                given()
                        .when()
                        .get("http://localhost:2000")
                        .then()
                        .statusCode(200)
                        .body(containsString("Shop API Showcase"));
                // @formatter:on
            } finally {
                System.setOut(original);
            }

            String console = out.toString();
            assertTrue(console.contains("INFO LogInterceptor"));
            assertTrue(console.contains("==== REQUEST  ==="));
            assertTrue(console.contains("==== RESPONSE  ==="));
        }
    }

    @Test
    void validMethodIsAccepted() {
        // GET matches the RFC 9110 token grammar accepted by the configured rfc9110MethodValidator.
        // @formatter:off
        given()
                .when()
                .request("GET", "http://localhost:2000")
                .then()
                .statusCode(200);
        // @formatter:on
    }

    @Test
    void overlongMethodIsRejected() {
        // AVERYLONGMETHODNAME is a valid RFC 9110 token, but the example caps methods at maxLength 15,
        // so it is rejected with 501 before reaching the API.
        // @formatter:off
        given()
                .when()
                .request("AVERYLONGMETHODNAME", "http://localhost:2000")
                .then()
                .statusCode(501);
        // @formatter:on
    }

    @Test
    void traceMethodIsRejected() {
        // TRACE is a valid RFC 9110 token, but the example configures allowTrace: false, so it is
        // rejected with 501 before reaching the API.
        // @formatter:off
        given()
                .when()
                .request("TRACE", "http://localhost:2000")
                .then()
                .statusCode(501);
        // @formatter:on
    }

}
