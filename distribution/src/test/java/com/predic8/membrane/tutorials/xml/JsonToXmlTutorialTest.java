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

public class JsonToXmlTutorialTest extends AbstractXmlTutorialTest {

    @Override
    protected String getTutorialYaml() {
        return "10-JSON-to-XML.yaml";
    }

    @Test
    void jsonIsConvertedToXml() throws IOException {
        // @formatter:off
        given()
            .body(readFileFromBaseDir("animals.json"))
            .contentType(JSON)
        .when()
            .post("http://localhost:2000")
        .then()
            .statusCode(200)
            .contentType(XML)
            .body("zoo.number", equalTo("2"))
            .body("zoo.animals.array.item.size()", greaterThanOrEqualTo(2))
            .body("zoo.animals.array.item.name", hasItems("Skye", "Molly"))
            .body("zoo.animals.array.item.species", hasItems("dog", "cat"))
            .body("zoo.animals.array.item.find { it.name == 'Skye' }.legs", equalTo("4"));
        // @formatter:on
    }
}
