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

import static com.predic8.membrane.core.http.MimeType.*;
import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

public abstract class AbstractManualSOAPProxyTutorialTest extends WSDLRewriterTutorialTest {

    @Override
    protected String getTutorialYaml() {
        return "90-Manual-SOAPProxy.yaml";
    }

    @Test
    void soapCallInvalid() throws IOException {
        // @formatter:off
        given()
            // File is read from FS uses the same file as the user
            .body(readFileFromBaseDir("invalid-city.soap.xml"))
            .contentType(TEXT_XML_UTF8)
        .when()
            .post("http://localhost:%d/my-service".formatted(getPort()))
        .then()
            .log().body()
            .body("Envelope.Body.Fault.faultstring", equalTo("WSDL message validation failed"))
            .body(containsString("INVALID") );
        // @formatter:on
    }
}
