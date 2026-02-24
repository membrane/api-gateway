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

import java.io.IOException;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class SoapArrayToJsonTutorialTest extends AbstractTransformationTutorialTest {

    @Override
    protected String getTutorialYaml() {
        return "60-SOAP-Array-to-JSON.yaml";
    }

    @Test
    void soapArrayIsConvertedToJsonArray() throws IOException {
        // @formatter:off
        given()
            .body(readFileFromBaseDir("fruits.soap.xml"))
            .contentType("text/xml")
        .when()
            .post("http://localhost:2000")
        .then()
            .log().ifValidationFails()
            .statusCode(200)
            .contentType("application/json")
            .body("fruits", hasSize(3))
            .body("fruits.name", hasItems("Apricot", "Date", "Papaya"))
            .body("fruits.find { it.name == 'Apricot' }.price", equalTo(3.8F));
        // @formatter:on
    }
}
