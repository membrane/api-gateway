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

import static com.predic8.membrane.core.Constants.*;
import static io.restassured.RestAssured.*;
import static io.restassured.http.ContentType.*;
import static org.hamcrest.Matchers.*;

public class WSDLRewriterTutorialTest extends AbstractSOAPTutorialTest {

    @Override
    protected String getTutorialYaml() {
        return "30-WSDL-Rewriter.yaml";
    }

    @Test
    void wsdl() {
        // @formatter:off
        given()
        .when()
            .get("http://localhost:2000/my-service?wsdl")
        .then()
            .statusCode(200)
            .contentType(XML)
            .body(containsString(WSDL_SOAP11_NS))

            // s:address/@location must be rewritten
            .body("definitions.service.port.address.@location", equalTo("https://my.host.example.com/my-service"));
        // @formatter:on
    }

    @Test
    void soapCall() throws IOException {
        // @formatter:off
        given()
            // File is read from FS use the same file as the user
            .body(readFileFromBaseDir("../data/city.soap.xml"))
        .when()
            .post("http://localhost:2000/my-service")
        .then()
            .body("Envelope.Body.getCityResponse.population", equalTo("34665600"));
         // @formatter:on
    }

    @Test
    void webServiceExplorer() {
        // @formatter:off
        given()
        .when()
            .get("http://localhost:2000/my-service")
        .then()
            .statusCode(200)
            .contentType(HTML)
            .body(containsString("Membrane API Gateway: CityService"));
        // @formatter:on
    }
}
