package com.predic8.membrane.tutorials.xml;

import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.*;

public class JsonToXmlTutorialTest extends AbstractXmlTutorialTest {

    @Override
    protected String getTutorialYaml() {
        return "10-JSON-to-XML.yaml";
    }

    @Test
    void jsonIsConvertedToXml() {
        // @formatter:off
        given()
            .body("sadasdsjson")
            .contentType(JSON)
        .when()
            .post("http://localhost:2000")
        .then()
            .statusCode(200)
            .contentType(anyOf(containsString("xml"), containsString("application/xml"), containsString("text/xml")))
            .body(startsWith("<?xml"))
            .body(anyOf(containsString("<zoo>")), containsString("</zoo>"));
        // @formatter:on
    }
}
