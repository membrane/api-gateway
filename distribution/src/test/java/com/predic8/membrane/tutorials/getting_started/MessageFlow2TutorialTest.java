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

package com.predic8.membrane.tutorials.getting_started;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MessageFlow2TutorialTest extends AbstractGettingStartedTutorialTest{

    @Override
    protected String getTutorialYaml() {
        return "30-Message-Flow2.yaml";
    }

    @Test
    void flowLogs() {
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
            System.out.println(console);
            assertTrue(console.contains("TODO"));
            assertTrue(console.contains("TODO"));
            assertTrue(console.contains("TODO"));
        }
    }
    // TODO Example currently not working. Fix yaml issues, then adjust test

}
