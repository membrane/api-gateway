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

package com.predic8.membrane.tutorials.xml;

import org.junit.jupiter.api.*;

import java.io.*;

import static io.restassured.RestAssured.*;
import static io.restassured.http.ContentType.*;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

public class XPathTutorialTest extends AbstractXmlTutorialTest {
    @Override
    protected String getTutorialYaml() {
        return "30-XPath.yaml";
    }

    @Test
    void xpathExtractsPropertiesAndSetsHeader() throws IOException {

        synchronized (System.out) {
            var out = new ByteArrayOutputStream();
            var original = System.out;
            System.setOut(new PrintStream(out));

            try {
                // @formatter:off+
                given()
                    .body(readFileFromBaseDir("animals.xml"))
                    .contentType(XML)
                .when()
                    .post("http://localhost:2000")
                .then()
                    .log().ifValidationFails()
                    .statusCode(200)
                    .contentType(TEXT)
                    .body(allOf(
                            containsString("Names: Skye,Molly,Biscuit,Sunny,Bubbles"),
                            containsString("Animals: dog,cat,rabbit,parrot,goldfish")
                    ));
                // @formatter:on
            } finally {
                System.setOut(original);
            }

            var console = out.toString();
            assertTrue(console.contains("# of animals: 5"));
            assertTrue(console.contains("Dog found!"));
        }
    }
}
