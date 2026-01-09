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

import java.io.*;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

public class WSDLMessageValidationTutorialTest extends AbstractCityServiceTest {

    @Override
    protected String getTutorialYaml() {
        return "40-WSDL-Message-Validation.yaml";
    }

    @Override
    @Test
    void soapCall() throws IOException {
        given()
            // File is read from FS use the same file as the user
            .body(readFileFromBaseDir("../data/invalid-city.soap.xml"))
        .when()
            .post("http://localhost:2000/city-service")
        .then()
            .body("Envelope.Body.Fault.faultstring", equalTo("WSDL message validation failed"));
    }
}
