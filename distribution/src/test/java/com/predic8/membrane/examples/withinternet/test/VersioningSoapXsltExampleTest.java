/* Copyright 2021 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.examples.withinternet.test;

import com.predic8.membrane.examples.util.*;
import org.junit.jupiter.api.*;

import java.io.*;

import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

public class VersioningSoapXsltExampleTest extends DistributionExtractingTestcase {

    String request_old;
    String request_new;

    @BeforeEach
    void setup() throws IOException {
        request_old = readFileFromBaseDir("request-old.xml");
        request_new = readFileFromBaseDir("request-new.xml");
    }

    @Override
    protected String getExampleDirName() {
        return "web-services-soap/versioning-soap-xslt";
    }

    @Test
    public void test() throws Exception {

        try (Process2 ignored1 = startServiceProxyScript()) {
            // @formatter:off
            given()
                .body(request_new)
                .post("http://localhost:2000/city-service")
            .then()
                .statusCode(200)
                .body("Envelope.Body.getCityResponse.population", equalTo("327000"));

            given()
                .body(request_old)
                .post("http://localhost:2000/city-service")
            .then()
                .statusCode(200)
                .body("Envelope.Body.getCityResponse.country", equalTo("Germany"));
            // @formatter:on
        }
    }

}
