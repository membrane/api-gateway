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
import static io.restassured.http.ContentType.XML;
import static org.hamcrest.Matchers.*;

public class XsdSchemaValidationTutorialTest extends AbstractXmlTutorialTest{
    @Override
    protected String getTutorialYaml() {
        return "50-XSD-Schema-validation.yaml";
    }

    @Test
    void validXmlPassesValidation() throws IOException {
        // @formatter:off
        given()
            .body(readFileFromBaseDir("book-valid.xml"))
            .contentType(XML)
        .when()
            .post("http://localhost:2000")
        .then()
            .statusCode(200)
            .body(equalTo("Ok"));
        // @formatter:on
    }

    @Test
    void invalidXmlFailsValidation() throws IOException {
        // @formatter:off
        given()
            .contentType(XML)
            .body(readFileFromBaseDir("book-invalid.xml"))
        .when()
            .post("http://localhost:2000")
        .then()
            .statusCode(400)
            .body("'problem-details'.status", equalTo("400"))
            .body("'problem-details'.title", containsStringIgnoringCase("validation"))
            .body("'problem-details'.validation.size()", greaterThan(0))
            .body("'problem-details'.validation.message", hasItem(containsString("year")));
        // @formatter:on
    }
}
