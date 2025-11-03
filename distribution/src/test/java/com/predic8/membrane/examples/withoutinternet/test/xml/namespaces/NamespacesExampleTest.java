/* Copyright 2024 predic8 GmbH, www.predic8.com

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License. */
package com.predic8.membrane.examples.withoutinternet.test.xml.namespaces;

import com.predic8.membrane.examples.util.*;
import org.junit.jupiter.api.*;

import static com.predic8.membrane.core.http.MimeType.TEXT_XML;
import static io.restassured.RestAssured.given;
import static io.restassured.filter.log.LogDetail.ALL;
import static org.hamcrest.Matchers.containsString;

public class NamespacesExampleTest extends AbstractSampleMembraneStartStopTestcase {

    @Override
    protected String getExampleDirName() {
        return "xml/namespaces";
    }

    @Test
    void namespaceAwareXPathExtraction() {
        String xmlBody = """
            <per:person id="77" xmlns:per="https://predic8.de/person">
              <per:name>Hans</per:name>
              <ns1:address xmlns:ns1="https://predic8.de/address">
                <ns1:city>Cologne</ns1:city>
              </ns1:address>
            </per:person>
            """;

        // @formatter:off
        given()
            .contentType(TEXT_XML)
            .body(xmlBody)
            .post("http://localhost:2000")
        .then()
            .log().ifValidationFails(ALL)
            .statusCode(200)
            .body(containsString("Hans from Cologne"));
        // @formatter:on
    }

    @Test
    void differentCity() {
        String xmlBody = """
            <per:person id="42" xmlns:per="https://predic8.de/person">
              <per:name>Maria</per:name>
              <ns1:address xmlns:ns1="https://predic8.de/address">
                <ns1:city>Berlin</ns1:city>
              </ns1:address>
            </per:person>
            """;

        // @formatter:off
        given()
            .contentType(TEXT_XML)
            .body(xmlBody)
            .post("http://localhost:2000")
        .then()
            .log().ifValidationFails(ALL)
            .statusCode(200)
            .body(containsString("Maria from Berlin"));
        // @formatter:on
    }

    @Test
    void differentPrefixesStillMatch() {
        String xmlBody = """
        <p:person id="13" xmlns:p="https://predic8.de/person">
          <p:name>Kim</p:name>
          <a:address xmlns:a="https://predic8.de/address">
            <a:city>Bonn</a:city>
          </a:address>
        </p:person>
        """;

        // @formatter:off
        given()
            .contentType(TEXT_XML)
            .body(xmlBody)
            .post("http://localhost:2000")
        .then()
            .log().ifValidationFails(ALL)
            .statusCode(200)
            .body(containsString("Kim from Bonn"));
        // @formatter:on
    }

    @Test
    void defaultNamespaceOnPerson() {
        String xmlBody = """
        <person id="5" xmlns="https://predic8.de/person">
          <name>Udo</name>
          <ns1:address xmlns:ns1="https://predic8.de/address">
            <ns1:city>Hamburg</ns1:city>
          </ns1:address>
        </person>
        """;

        // @formatter:off
        given()
            .contentType(TEXT_XML)
            .body(xmlBody)
            .post("http://localhost:2000")
        .then()
            .log().ifValidationFails(ALL)
            .statusCode(200)
            .body(containsString("Udo from Hamburg"));
        // @formatter:on
    }

    @Test
    void noNamespacesShouldNotMatch() {
        String xmlBody = """
        <person id="7">
          <name>Max</name>
          <address>
            <city>Cologne</city>
          </address>
        </person>
        """;

        // @formatter:off
        given()
            .contentType(TEXT_XML)
            .body(xmlBody)
            .post("http://localhost:2000")
        .then()
            .log().ifValidationFails(ALL)
            .statusCode(200)
            .body(containsString("from"));
        // @formatter:on
    }

    @Test
    void wrongNamespaceOnPerson() {
        String xmlBody = """
        <per:person id="88" xmlns:per="https://predic8.de/personWRONG">
          <per:name>Hans</per:name>
          <ns1:address xmlns:ns1="https://predic8.de/address">
            <ns1:city>Cologne</ns1:city>
          </ns1:address>
        </per:person>
        """;

        // @formatter:off
        given()
            .contentType(TEXT_XML)
            .body(xmlBody)
            .post("http://localhost:2000")
        .then()
            .log().ifValidationFails(ALL)
            .statusCode(200)
            .body(containsString("from Cologne"));
        // @formatter:on
    }

    @Test
    void wrongNamespaceOnAddress() {
        String xmlBody = """
        <per:person id="88" xmlns:per="https://predic8.de/person">
          <per:name>Hans</per:name>
          <ns1:address xmlns:ns1="https://predic8.de/addressWRONG">
            <ns1:city>Cologne</ns1:city>
          </ns1:address>
        </per:person>
        """;

        // @formatter:off
        given()
            .contentType(TEXT_XML)
            .body(xmlBody)
            .post("http://localhost:2000")
        .then()
            .log().ifValidationFails(ALL)
            .statusCode(200)
            .body(containsString("Hans from"));
        // @formatter:on
    }

}
