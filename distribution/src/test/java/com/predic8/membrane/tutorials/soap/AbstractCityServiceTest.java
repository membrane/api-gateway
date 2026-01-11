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

import static com.predic8.membrane.core.Constants.WSDL_SOAP11_NS;
import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.XML;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

public abstract class AbstractCityServiceTest extends AbstractSOAPTutorialTest {

    @Test
    void wsdl() {
        // @formatter:off
        given()
        .when()
            .get("http://localhost:2000/city-service?wsdl")
        .then()
            .log().body()
            .statusCode(200)
            .contentType(XML)
            .body(containsString(WSDL_SOAP11_NS))

            // s:address/@location must be rewritten
            .body("definitions.service.port.address.@location", equalTo("http://localhost:2000/city-service"));
        // @formatter:on
    }

    @Test
    void soapCall() throws IOException {
        given()
            // File is read from FS use the same file as the user
            .body(readFileFromBaseDir("../data/city.soap.xml"))
        .when()
            .post("http://localhost:2000/city-service")
        .then()
            .log().body()
            .body("Envelope.Body.getCityResponse.population", equalTo("34665600"));
    }

}
