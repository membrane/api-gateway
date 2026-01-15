package com.predic8.membrane.tutorials.xml;

import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.XML;
import static org.hamcrest.Matchers.*;

public class XmlToJsonTutorialTest extends AbstractXmlTutorialTest {

    @Override
    protected String getTutorialYaml() {
        return "20-XML-to-JSON.yaml";
    }

    @Test
    void xmlIsConvertedToJson() {
        // @formatter:off
        given()
            .body(classpathText("animals.xml"))
            .contentType(XML)
        .when()
            .post("http://localhost:2000")
        .then()
            .statusCode(200)
            .contentType(anyOf(containsString("json"), containsString("application/json"), containsString("text/json")))
            .body(startsWith("{"))
            .body(anyOf(containsString("\"zoo\""), containsString("\"animal\"")));
        // @formatter:on
    }
}
