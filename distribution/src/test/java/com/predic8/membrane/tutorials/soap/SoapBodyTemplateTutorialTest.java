/* Copyright 2025 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.tutorials.soap;

import org.junit.jupiter.api.*;

import static io.restassured.RestAssured.*;
import static io.restassured.http.ContentType.*;
import static org.hamcrest.Matchers.*;

public class SoapBodyTemplateTutorialTest extends AbstractSOAPTutorialTest {

    @Override
    protected String getTutorialYaml() {
        return "60-SOAP-Body-Template.yaml";
    }

    @Test
    void soapBodyTemplateResponse() {
        // @formatter:off
        given()
        .when()
            .get("http://localhost:2000/service-mock")
        .then()
            .statusCode(200)
            .contentType(XML)
            .body("Envelope.Body.getCityResponse.country", equalTo("England"))
            .body("Envelope.Body.getCityResponse.population", equalTo("8980000"));
        // @formatter:on
    }
}
