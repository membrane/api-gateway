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

package com.predic8.membrane.tutorials.soap;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static com.predic8.membrane.core.http.MimeType.TEXT_XML_UTF8;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

public class WSDLHeaderValidationTutorialTest extends AbstractSOAPTutorialTest {

    @Override
    protected String getTutorialYaml() {
        return "41-WSDL-Header-Validation.yaml";
    }

    @Test
    void validHeader() throws IOException {
        // @formatter:off
        given()
            .body(readFileFromBaseDir("city-with-header.soap.xml"))
            .contentType(TEXT_XML_UTF8)
        .when()
            .post("http://localhost:2000/city-service")
        .then()
            .log().body()
            .body("Envelope.Body.getCityResponse.population", equalTo("34665600"));
        // @formatter:on
    }

    @Test
    void invalidHeader() throws IOException {
        // @formatter:off
        given()
            .body(readFileFromBaseDir("invalid-header.soap.xml"))
            .contentType(TEXT_XML_UTF8)
        .when()
            .post("http://localhost:2000/city-service")
        .then()
            .log().body()
            .body("Envelope.Body.Fault.faultstring", equalTo("WSDL message validation failed"))
            .body(containsString("abc"));
        // @formatter:on
    }

    @Test
    void missingHeader() throws IOException {
        // @formatter:off
        given()
            .body(readFileFromBaseDir("city.soap.xml"))
            .contentType(TEXT_XML_UTF8)
        .when()
            .post("http://localhost:2000/city-service")
        .then()
            .log().body()
            .body("Envelope.Body.Fault.faultstring", equalTo("WSDL message validation failed"))
            .body(containsString("{https://predic8.de/cities}Client required by the WSDL binding is missing"));
        // @formatter:on
    }
}
