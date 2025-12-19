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
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.jupiter.api.Assertions.assertTrue;


public class PathRewritingTutorialTest extends AbstractAdvancedTutorialTest{

    @Override
    protected String getTutorialYaml() {
        return "30-Path-Rewriting.yaml";
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
                    .get("http://localhost:2000/fruits/7")
                .then()
                    .statusCode(200)
                    .body("id", equalTo(7))
                    .body("name",  notNullValue())
                    .body("modified_at", notNullValue());
                // @formatter:on
            } finally {
                System.setOut(original);
            }

            String console = out.toString();
            System.out.println(console);
            assertTrue(console.contains("INFO LogInterceptor"));
            assertTrue(console.contains("Requested /fruits/7"));
            assertTrue(console.contains("Rewritten /shop/v2/products/7"));
        }
    }
}