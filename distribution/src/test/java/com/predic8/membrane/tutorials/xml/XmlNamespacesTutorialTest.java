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

public class XmlNamespacesTutorialTest extends AbstractXmlTutorialTest {
    @Override
    protected String getTutorialYaml() {
        return "40-XML-Namespaces.yaml";
    }

    @Test
    void globalNamespacesAreResolved() throws IOException {
        // @formatter:off
        given()
            .body(readFileFromBaseDir("order.xml"))
            .contentType(XML)
        .when()
            .post("http://localhost:2000")
        .then()
            .log().ifValidationFails()
            .statusCode(200)
            .contentType(TEXT)
            .body(allOf(
                    containsString("Order: 4711"),
                    containsString("Customer: Alice"),
                    containsString("First item: Pen"),
                    containsString("Items: 2")
            ));
        // @formatter:on
    }

    @Test
    void otherDocumentGoesToFallback() {
        // @formatter:off
        given()
            .body("<order><id>4711</id></order>")
            .contentType(XML)
        .when()
            .post("http://localhost:2000")
        .then()
            .log().ifValidationFails()
            .statusCode(404)
            .body(equalTo("No order!"));
        // @formatter:on
    }
}
