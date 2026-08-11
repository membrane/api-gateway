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

package com.predic8.membrane.tutorials.webservicessecurity;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

public class AddAndValidateTimestampTutorialTest extends AbstractWebServicesSecurityTutorialTest {

    @Override
    protected String getTutorialYaml() {
        return "30-Add-And-Validate-Timestamp.yaml";
    }

    @Test
    void addsTimestampAndForwardsToValidatedBackend() throws IOException {
        // @formatter:off
        given()
            .body(readFileFromBaseDir("request.xml"))
            .contentType("text/xml")
        .when()
            .post("http://localhost:2000")
        .then()
            .statusCode(200)
            .body("Envelope.Body.getCityResponse.population", equalTo("34665600"));
        // @formatter:on
    }

    @Test
    void rejectsExpiredTimestampAtValidator() throws IOException {
        // @formatter:off
        given()
            .body(readFileFromBaseDir("request-expired-timestamp.xml"))
            .contentType("text/xml")
        .when()
            .post("http://localhost:2001")
        .then()
            .body("Envelope.Body.Fault.faultcode", equalTo("wsse:MessageExpired"));
        // @formatter:on
    }
}
