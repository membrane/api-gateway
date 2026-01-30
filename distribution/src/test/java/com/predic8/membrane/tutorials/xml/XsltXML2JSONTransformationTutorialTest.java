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

public class XsltXML2JSONTransformationTutorialTest extends AbstractXmlTutorialTest{
    @Override
    protected String getTutorialYaml() {
        return "35-XSLT-Transformation-to-json.yaml";
    }

    @Test
    void xsltTransformsXml() throws IOException {
        // @formatter:off
        given()
            .body(readFileFromBaseDir("books.xml"))
            .contentType(XML)
        .when()
            .post("http://localhost:2000")
        .then()
            .statusCode(200)
            .contentType(JSON)
            .body("books.size()", greaterThan(0))
            .body("books[0].year", equalTo("1975"));
        // @formatter:on
    }
}
