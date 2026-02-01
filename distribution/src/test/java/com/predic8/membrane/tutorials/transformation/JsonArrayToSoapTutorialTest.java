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
import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;

public class JsonArrayToSoapTutorialTest extends AbstractTransformationTutorialTest {

    @Override
    protected String getTutorialYaml() {
        return "50-JSON-Array-to-SOAP.yaml";
    }

    @Test
    void jsonArrayIsRenderedAsSoapArray() throws IOException {
        // @formatter:off
        given()
            .body(readFileFromBaseDir("fruits.json"))
            .contentType(JSON)
        .when()
            .post("http://localhost:2000")
        .then()
            .statusCode(200)
            .contentType("text/xml")
            .body("Envelope.Body.getFruitsResponse.fruit.size()", equalTo(3))
            .body("Envelope.Body.getFruitsResponse.fruit.name", hasItems("Apricot", "Date", "Papaya"));
        // @formatter:on
    }
}
