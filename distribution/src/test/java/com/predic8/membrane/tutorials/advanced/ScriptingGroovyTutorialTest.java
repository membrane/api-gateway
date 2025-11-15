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

package com.predic8.membrane.tutorials.advanced;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertTrue;


public class ScriptingGroovyTutorialTest extends AbstractAdvancedTutorialTest {

    @Override
    protected String getTutorialYaml() {
        return "70-Scripting-Groovy.yaml";
    }

    @Test
    void groovyEndpointLogs() {
        synchronized (System.out) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PrintStream original = System.out;
            System.setOut(new PrintStream(out));

            try {
                // @formatter:off
                given()
                .when()
                    .get("http://localhost:2000/groovy")
                .then()
                    .statusCode(200);
                // @formatter:on
            } finally {
                System.setOut(original);
            }

            String console = out.toString();
            assertTrue(console.contains("I'm executed in the REQUEST flow"));
            assertTrue(console.contains("I'm executed in the RESPONSE flow"));
        }
    }

    @Test
    void randomEndpoint() {
        // @formatter:off
        given()
        .when()
            .get("http://localhost:2000/random")
        .then()
            .body(notNullValue());
        // @formatter:on
    }

    @Test
    void groovyCustomResponse() {
        // @formatter:off
        given()
        .when()
            .get("http://localhost:2000/response")
        .then()
            .statusCode(200)
            .contentType("application/json")
            .header("X-Foo", "bar")
            .body("success", equalTo(true));
        // @formatter:on
    }

}
