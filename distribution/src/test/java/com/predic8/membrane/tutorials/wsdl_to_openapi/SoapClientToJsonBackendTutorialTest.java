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

package com.predic8.membrane.tutorials.wsdl_to_openapi;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.XML;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

public class SoapClientToJsonBackendTutorialTest extends AbstractWsdl2OpenAPITutorialTest {

    @Override
    protected String getTutorialYaml() {
        return "60-SOAP-Client-to-JSON-Backend.yaml";
    }

    /** Step 2: the JSON of the backend comes back as the SOAP response of getPartner, in schema order. */
    @Test
    void soapRequestGetsSoapResponseFromJsonBackend() throws IOException {
        // @formatter:off
        given()
            .contentType("text/xml")
            .body(readFileFromBaseDir("get-partner-1.soap.xml"))
        .when()
            .post("http://localhost:2000")
        .then()
            .statusCode(200)
            .contentType(XML)
            .body(containsString("<partner><id>1</id><name>Alice</name><birthDate>1985-03-15</birthDate>"))
            .body(containsString("<city>Springfield</city>"));
        // @formatter:on
    }

    /** Step 4: a 404 of the backend becomes a Client fault, without the backend's text. */
    @Test
    void backendErrorBecomesSoapFault() throws IOException {
        // @formatter:off
        given()
            .contentType("text/xml")
            .body(readFileFromBaseDir("get-partner-7.soap.xml"))
        .when()
            .post("http://localhost:2000")
        .then()
            .statusCode(500)
            .body(containsString("<faultcode>soap:Client</faultcode>"))
            .body(containsString("The service rejected the request."))
            .body(not(containsString("Partner not found")));
        // @formatter:on
    }
}
