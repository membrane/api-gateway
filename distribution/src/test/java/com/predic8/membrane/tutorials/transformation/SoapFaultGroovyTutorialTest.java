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

package com.predic8.membrane.tutorials.transformation;

import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.equalTo;

public class SoapFaultGroovyTutorialTest extends AbstractTransformationTutorialTest {

    @Override
    protected String getTutorialYaml() {
        return "80-SOAP-Fault-Groovy.yaml";
    }

    @Test
    void soapResponseIsConvertedToJson() {
        // @formatter:off
        given()
            .contentType(JSON)
            .body("{\"name\":\"Plum\",\"price\":1.25}")
        .when()
            .post("http://localhost:2000/products")
        .then()
            .log().ifError()
            .statusCode(200)
            .body("success.id", equalTo("EF-0082"))
            .body("success.category", equalTo("Exotic fruits"));
        // @formatter:on
    }

    @Test
    void soapFaultIsConvertedToJsonError() {
        // @formatter:off
        given()
            .contentType(JSON)
            .header("X-Mock-Mode", "fault")
            .body("{\"name\":\"Plum\",\"price\":1.25}")
        .when()
            .post("http://localhost:2000/products")
        .then()
            .log().ifError()
            .statusCode(200)
            .body("error.code", equalTo("s11:Server"))
            .body("error.message", equalTo("Error creating product"));
        // @formatter:on
    }
}
