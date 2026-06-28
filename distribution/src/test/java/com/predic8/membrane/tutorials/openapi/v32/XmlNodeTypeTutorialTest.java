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

package com.predic8.membrane.tutorials.openapi.v32;

import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;

public class XmlNodeTypeTutorialTest extends AbstractOpenAPIV32TutorialTest {

    @Override
    protected String getTutorialYaml() {
        return "40-XML-nodeType.apis.yaml";
    }

    @Test
    void validXmlIsForwarded() {
        // @formatter:off
        given()
            .contentType("application/xml")
            .body("<order id=\"A1\"><total currency=\"USD\">42.50</total></order>")
        .when()
            .post("http://localhost:2000/orders")
        .then()
            .statusCode(201);
        // @formatter:on
    }

    @Test
    void nonNumericTotalIsRejected() {
        // @formatter:off
        given()
            .contentType("application/xml")
            .body("<order id=\"A1\"><total currency=\"USD\">cheap</total></order>")
        .when()
            .post("http://localhost:2000/orders")
        .then()
            .statusCode(400)
            .body(containsString("value"));
        // @formatter:on
    }
}
