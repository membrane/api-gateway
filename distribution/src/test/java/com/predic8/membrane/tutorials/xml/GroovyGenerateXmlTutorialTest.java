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

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static io.restassured.http.ContentType.XML;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;

public class GroovyGenerateXmlTutorialTest extends AbstractXmlTutorialTest {
    @Override
    protected String getTutorialYaml() {
        return "90-Groovy-Generate-XML.yaml";
    }

    @Test
    void generatesXmlFromJson() {
        // @formatter:off
        given()
            .contentType(JSON)
            .body("""
                {"title":"Design Patterns","year":1994,"authors":["Erich Gamma","Richard Helm"]}""")
        .when()
            .post("http://localhost:2000")
        .then()
            .statusCode(200)
            .contentType(XML)
            .body("book.@year", equalTo("1994"))
            .body("book.title", equalTo("Design Patterns"))
            .body("book.authors.author", contains("Erich Gamma", "Richard Helm"));
        // @formatter:on
    }
}
