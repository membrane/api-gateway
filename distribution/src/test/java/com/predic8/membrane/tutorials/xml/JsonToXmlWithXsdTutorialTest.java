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

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static io.restassured.http.ContentType.XML;
import static org.hamcrest.Matchers.*;

public class JsonToXmlWithXsdTutorialTest extends AbstractXmlTutorialTest {

    @Override
    protected String getTutorialYaml() {
        return "15-JSON-to-XML-with-XSD.yaml";
    }

    @Test
    void theXmlSchemaDecidesOrderAttributesAndArrays() throws IOException {
        // @formatter:off
        final var xml = given()
            .body(readFileFromBaseDir("one-book.json"))
            .contentType(JSON)
        .when()
            .post("http://localhost:2000")
        .then()
            .log().ifValidationFails()
            .statusCode(200)
            .contentType(XML)
            .body("books.book.@id", equalTo("1"))
            .body("books.book.title", equalTo("The Mythical Man-Month"))
            .body("books.book.author", equalTo("Frederick P. Brooks Jr."))
            .extract().asString();
        // @formatter:on

        assertOrder(xml, "<title>", "<author>", "<category>");
    }

    @Test
    void aRootTheXmlSchemaDoesNotDeclareIsRejected() {
        // @formatter:off
        given()
            .body("{\"magazine\": {}}")
            .contentType(JSON)
        .when()
            .post("http://localhost:2000")
        .then()
            .log().ifValidationFails()
            .statusCode(400);
        // @formatter:on
    }

    private static void assertOrder(String xml, String... tags) {
        var last = -1;
        for (var tag : tags) {
            final var index = xml.indexOf(tag);
            if (index <= last) throw new AssertionError("expected %s after the previous element in XSD order: %s".formatted(tag, xml));
            last = index;
        }
    }
}
