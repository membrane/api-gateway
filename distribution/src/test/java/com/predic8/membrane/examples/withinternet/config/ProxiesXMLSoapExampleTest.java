/* Copyright 2023 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.examples.withinternet.config;

import com.predic8.membrane.examples.util.*;
import org.junit.jupiter.api.*;

import static com.predic8.membrane.core.http.MimeType.*;
import static io.restassured.RestAssured.*;
import static io.restassured.filter.log.LogDetail.*;
import static org.hamcrest.Matchers.*;

public class ProxiesXMLSoapExampleTest extends AbstractSampleMembraneStartStopTestcase {

    @Override
    protected String getExampleDirName() {
        return "..";
    }

    @BeforeEach
    void startMembrane() throws Exception {
        process = new Process2.Builder().in(baseDir).script("membrane").parameters("-c conf/proxies-soap.xml").waitForMembrane().start();

        // Dump HTTP
        // RestAssured.filters(new RequestLoggingFilter(), new ResponseLoggingFilter());
    }

    @Test
    void getWebServicesExplorer2000() {
        get(LOCALHOST_2000 + "/city-service")
        .then().assertThat()
            .log().ifValidationFails(ALL)
            .statusCode(200)
            .contentType(TEXT_HTML)
            .body("html.head.title",containsString("CityService"));
    }

    @Test
    void getWebServicesExplorer2001() {
        get("http://localhost:2001/city-service")
                .then().assertThat()
                .statusCode(200)
                .contentType(TEXT_HTML)
                .body("html.head.title",containsString("CityService"));
    }

    @Test
    void getWSDL2000() {
        get("http://localhost:2000/city-service?wsdl")
                .then().assertThat()
                .statusCode(200)
                .contentType(TEXT_XML)
                .body("definitions.service.@name", equalTo("CityService"));
    }

    @Test
    void getWSDL2001() {
        get("http://localhost:2001/city-service?wsdl")
                .then().assertThat()
                .statusCode(200)
                .contentType(TEXT_XML)
                .body("definitions.service.@name", equalTo("CityService"));
    }
}