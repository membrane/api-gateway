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
package com.predic8.membrane.examples.tests.error_handling.custom_error_messages;

import com.predic8.membrane.examples.util.AbstractSampleMembraneStartStopTestcase;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.XML;
import static org.hamcrest.CoreMatchers.containsString;

public class CustomErrorHandlingExampleTest extends AbstractSampleMembraneStartStopTestcase {

    @Override
    protected String getExampleDirName() {
        return "error-handling/custom-error-messages";
    }

    // @formatter:off
    @Test
    void caseA() {
        given()
            .queryParam("case", "a")
            .contentType(XML)
            .body("<foo a=\"1\" b=\"2\" c=\"3\" d=\"4\" e=\"5\" f=\"6\" g=\"7\" h=\"8\" i=\"10\" j=\"to much\"/>")
        .when()
            .post("/v1")
        .then()
            .statusCode(200)
            .body(containsString("<case>a</case>"));
    }

    @Test
    void caseB() {
        given()
                .queryParam("case", "b")
                .contentType(XML)
                .body("<wrong/>")
                .when()
                .post()
                .then()
                .statusCode(400)
                .body(containsString("TODO"));
    }

    @Test
    void caseC() {
        given()
                .queryParam("case", "c")
                .when()
                .get()
                .then()
                .statusCode(404)
                .body(containsString("TODO"));
    }

    @Test
    void caseD() {
        given()
                .queryParam("case", "d")
                .when()
                .get()
                .then()
                .statusCode(400)
                .body(containsString("TODO"));
    }

    @Test
    void caseE() {
        given()
                .contentType(XML)
                .body("""
                <s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:cs=\"https://predic8.de/cities\">
                    <s:Body>
                        <cs:getCity>
                            <name>Verursache SOAP Fault!</name>
                        </cs:getCity>
                    </s:Body>
                </s:Envelope>
            """.stripIndent())
                .when()
                .post()
                .then()
                .statusCode(500)
                .body(containsString("TODO"));
    }

    @Test
    void caseF() {
        given()
                .contentType(XML)
                .body("""
                <s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:cs=\"https://predic8.de/cities\">
                    <s:Body>
                        <cs:getCity>
                            <name>Bonn</name>
                        </cs:getCity>
                    </s:Body>
                </s:Envelope>
            """.stripIndent())
                .when()
                .post()
                .then()
                .statusCode(200)
                .body(containsString("TODO"));
    }

    @Test
    void caseG() {
        given()
                .queryParam("case", "g")
                .when()
                .get()
                .then()
                .statusCode(502)
                .body(containsString("TODO"));
    }

    // formatter:on
}