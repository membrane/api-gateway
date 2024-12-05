/* Copyright 2024 predic8 GmbH, www.predic8.com

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

import com.predic8.membrane.examples.util.AbstractSampleMembraneStartStopTestcase;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConditionalInterceptorTest extends AbstractSampleMembraneStartStopTestcase {

    @Override
    protected String getExampleDirName() {
        return "if";
    }

    @ParameterizedTest
    @MethodSource("provideApiTestCases")
    void testApi(String method, String headers, String body, String params, int expectedStatus, String expectedOutput) {

        ByteArrayOutputStream outContent = new ByteArrayOutputStream();

        //Captures System.out output to verify expected console output in the test.
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(outContent));

        try {
            switch (method) {
                case "GET":
                    given()
                        .headers(parseHeader(headers))
                    .when()
                        .get("http://localhost:2000" + params)
                    .then()
                        .statusCode(expectedStatus);
                    break;

                case "POST":
                    given()
                        .headers(parseHeader(headers))
                        .body(body)
                    .when()
                        .post("http://localhost:2000" + params)
                    .then()
                        .statusCode(expectedStatus);
                    break;

                default:
                    throw new IllegalArgumentException("Unsupported method: " + method);
            }

            assertTrue(outContent.toString().contains(expectedOutput), "Expected output not found in console");
        } finally {
            System.setOut(originalOut);
        }
    }

    private static Stream<Arguments> provideApiTestCases() {
        return Stream.of(
                Arguments.of("GET", "Content-Type:application/json", "", "/", 404, "JSON Request!"),
                Arguments.of("POST", "Content-Type:application/json", "{\"name\": \"bar\"}", "/", 404, "The JSON request contains the key 'name', and it is not null."),
                Arguments.of("POST", "Content-Type:application/json", "{\"name\": \"foo\"}", "/", 404, "The JSON request contains the key 'name' with the value 'foo'."),
                Arguments.of("POST", "", "", "", 404, "Request method was POST."),
                Arguments.of("GET", "", "", "?param1=value2", 404, "Query Parameter Given!"),
                Arguments.of("GET", "X-Test-Header:foobar", "", "/", 404, "X-Test-Header contains 'bar'"),
                Arguments.of("POST", "Content-Type:application/xml", "", "/", 404, ""),
                Arguments.of("POST", "Content-Type:application/json", "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.", "?param1=value1", 404, "Long body"),
                Arguments.of("GET", "Content-Type:application/json", "", "?param1=value2", 404, "Status code changed")
        );
    }

    private static Map<String, String> parseHeader(String header) {
        if(header.isEmpty()) return new HashMap<>();
        String[] parts = header.split(":", 2);
        return Map.of(parts[0], parts[1]);
    }

}

