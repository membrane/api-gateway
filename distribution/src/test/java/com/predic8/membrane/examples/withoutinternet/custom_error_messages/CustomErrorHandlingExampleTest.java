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
package com.predic8.membrane.examples.withoutinternet.custom_error_messages;

import com.predic8.membrane.examples.util.*;
import org.junit.jupiter.api.*;

import static com.predic8.membrane.core.http.MimeType.*;
import static io.restassured.RestAssured.*;
import static io.restassured.http.ContentType.*;
import static org.hamcrest.CoreMatchers.*;

public class CustomErrorHandlingExampleTest extends AbstractSampleMembraneStartStopTestcase {

    @Override
    protected String getExampleDirName() {
        return "extending-membrane/error-handling/custom-error-messages";
    }

    // @formatter:off
    @Test
    void caseA() {
        given()
            .queryParam("case", "a")
            .contentType(TEXT_XML)
            .body("""
                    <foo a="1" b="2" c="3" d="4" e="5" f="6" g="7" h="8" i="9" j="10" k="too much"/>""")
        .when()
            .post("http://localhost:2000/service")
        .then()
            .log().ifValidationFails()
            .statusCode(500)
            .body(
                    containsString("<case>a</case>"),
                    containsString("XML Protection: Invalid XML!")
            );
    }

    @Test
    void caseB() {
        given()
            .queryParam("case", "b")
            .contentType(XML)
            .body("<wrong/>")
        .when()
            .post("http://localhost:2000/service")
        .then()
            .statusCode(200)
            .body(
                    containsString("<case>b</case>"),
                    containsString("WSDL validation of REQUEST failed!")
            );
    }

    @Test
    void caseC() {
        given()
            .queryParam("case", "c")
        .when()
            .get("http://localhost:2000/service")
        .then()
            .statusCode(500)
            .body(
                    containsString("<case>c</case>"),
                    containsString("Ordinary Error!")
            );
    }

    @Test
    void caseD() {
        given()
            .queryParam("case", "d")
        .when()
            .get("http://localhost:2000/service")
        .then()
            .statusCode(500)
            .body(
                    containsString("<case>d</case>"),
                    containsString("<message>XML Fehler Meldung vom Backend!!</message>")
            );
    }

    @Test
    void caseE() {
        given()
            .contentType(XML)
            .body("""
                <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" xmlns:cs="https://predic8.de/cities">
                    <s:Body>
                        <cs:getCity>
                            <name>Verursache SOAP Fault!</name>
                        </cs:getCity>
                    </s:Body>
                </s:Envelope>""".stripIndent())
        .when()
            .post("http://localhost:2000/service")
        .then()
            .statusCode(200)
            .body(
                    containsString("<case>e</case>"),
                    containsString("<fault>Not Found</fault>")
            );
    }

    @Test
    void caseF() {
        given()
            .contentType(XML)
            .body("""
                <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" xmlns:cs="https://predic8.de/cities">
                    <s:Body>
                        <cs:getCity>
                            <name>Bonn</name>
                        </cs:getCity>
                    </s:Body>
                </s:Envelope>""".stripIndent())
        .when()
            .post("http://localhost:2000/service")
        .then()
            .statusCode(200)
            .body(
                    containsString("<country>Germany</country>"),
                    containsString("<population>327000</population>")
            );
    }

    // formatter:on
}