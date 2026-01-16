package com.predic8.membrane.tutorials.xml;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static io.restassured.http.ContentType.XML;
import static org.hamcrest.Matchers.*;

public class JsonToXmlTutorialTest extends AbstractXmlTutorialTest {

    @Override
    protected String getTutorialYaml() {
        return "10-JSON-to-XML.yaml";
    }

    @Test
    void jsonIsConvertedToXml() throws IOException {
        // @formatter:off
        given()
            .body(readFileFromBaseDir("animals.json"))
            .contentType(JSON)
        .when()
            .post("http://localhost:2000")
        .then()
            .statusCode(200)
            .contentType(XML)
            .body(startsWith("<?xml"))
            .body(containsString("<zoo>"))
            .body(containsString("</zoo>"))
            .body(containsString("<number>2</number>"))
            .body(containsString("<animals>"))
            .body(containsString("<array>"))
            .body(containsString("<item>"))
            .body(containsString("<species>dog</species>"))
            .body(containsString("<legs>4</legs>"))
            .body(containsString("<name>Skye</name>"))
            .body(containsString("<species>cat</species>"))
            .body(containsString("<name>Molly</name>"));
        // @formatter:on
    }
}
