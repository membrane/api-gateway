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

package com.predic8.membrane.examples.withinternet;

import com.predic8.membrane.examples.util.*;
import org.junit.jupiter.api.*;

import java.io.*;

import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.util.FileUtil.*;
import static io.restassured.RestAssured.*;
import static io.restassured.http.ContentType.*;
import static org.hamcrest.Matchers.*;

public class TutorialSoapExampleTest extends AbstractSampleMembraneStartStopTestcase {

    @Override
    protected String getExampleDirName() {
        return "../tutorials/soap";
    }

    @BeforeEach
    void startMembrane() throws IOException, InterruptedException {
        writeInputStreamToFile(baseDir + "/proxies.xml", getResourceAsStream("com/predic8/membrane/examples/tutorials/soap/soap-tutorial-steps-proxies.xml"));
        process = startServiceProxyScript();
    }

    @Test
    void step1() {
        // @formatter:off
        given()
        .when()
            .get("http://localhost:2000/city-service")
        .then()
            .statusCode(200)
            .contentType(HTML)
            .body(containsString("Copyright"));

        given()
        .when()
            .get("http://localhost:9000/admin")
        .then()
            .statusCode(200)
            .contentType(HTML)
            .body(containsString("Statistics"));
        // @formatter:on
    }

    @Test
    void step2() {
        // @formatter:off
        given()
        .when()
            .body(validSoapRequest)
            .contentType(TEXT_XML)
            .post("http://localhost:2001/soap-service")
        .then()
            .statusCode(200)
            .contentType(TEXT_XML)
            .body(containsString("England"));
        // @formatter:on
    }

    @Test
    void step3() {
        // @formatter:off
        given()
        .when()
            .body(invalidSoapRequest)
            .contentType(TEXT_XML)
            .post("http://localhost:2001/soap-service")
        .then()
            .statusCode(200)
            .contentType(TEXT_XML)
            .body(containsString("<faultcode>s11:Client</faultcode>"));
        // @formatter:on
    }

    @Test
    void step4() {
        // @formatter:off
        given()
        .when()
            .body(invalidSoapRequest)
            .contentType(TEXT_XML)
        .post("http://localhost:2002/soap-service")
            .then()
            .statusCode(200)
            .contentType(TEXT_XML)
            .body(containsString("<faultstring>WSDL message validation failed</faultstring>"));
        // @formatter:on
    }

    String validSoapRequest = """
            <s11:Envelope xmlns:s11='http://schemas.xmlsoap.org/soap/envelope/'>
              <s11:Body>
                <cs:getCity xmlns:cs='https://predic8.de/cities'>
                  <name>London</name>
                </cs:getCity>
              </s11:Body>
            </s11:Envelope>
            """;

    String invalidSoapRequest = """
            <s11:Envelope xmlns:s11='http://schemas.xmlsoap.org/soap/envelope/'>
              <s11:Body>
                <cs:getCity xmlns:cs='https://predic8.de/cities'>
                  <foo>London</foo>
                </cs:getCity>
              </s11:Body>
            </s11:Envelope>
            """;
}