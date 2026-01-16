package com.predic8.membrane.tutorials.security;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.XML;
import static org.hamcrest.Matchers.*;

public class XmlProtectionTutorialTest extends AbstractSecurityTutorialTest {

    @Override
    protected String getTutorialYaml() {
        return "90-XML-Protection.yaml";
    }

    @Test
    void suspiciousXmlIsBlocked() {
        // @formatter:off
        given()
            .body("<foo a=\"1\" b=\"2\" c=\"3\" d=\"4\"/>")
            .contentType(XML)
        .when()
            .post("http://localhost:2000")
        .then()
            .statusCode(anyOf(is(500), is(400)))
            .header("X-Protection", containsString("XML security policy"))
            .body(anyOf(
                    containsString("Content violates XML security policy"),
                    containsString("xml-protection"),
                    containsString("<problem-details>")
            ));
    // @formatter:on
    }

    @Test
    void dtdIsAllowed() throws IOException {
        // @formatter:off
        given()
            .body(readFileFromBaseDir("hello-dtd.xml"))
            .contentType(XML)
        .when()
            .post("http://localhost:2000")
        .then()
            .statusCode(200)
            .contentType(XML)
            .body(containsString("<n>Hello</n>"));
        // @formatter:on
    }

}
