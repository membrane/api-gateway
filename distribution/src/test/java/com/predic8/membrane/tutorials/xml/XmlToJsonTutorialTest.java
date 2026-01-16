package com.predic8.membrane.tutorials.xml;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static io.restassured.http.ContentType.XML;
import static org.hamcrest.Matchers.*;

public class XmlToJsonTutorialTest extends AbstractXmlTutorialTest {

    @Override
    protected String getTutorialYaml() {
        return "20-XML-to-JSON.yaml";
    }

    @Test
    void xmlIsConvertedToJson() throws IOException {
        // @formatter:off
        given()
            .body(readFileFromBaseDir("animals.xml"))
            .contentType(XML)
        .when()
            .post("http://localhost:2000")
        .then()
            .statusCode(200)
            .contentType(JSON)
            .body("animals", notNullValue())
            .body("animals.animal", notNullValue())
            .body("animals.animal.size()", equalTo(5))
            .body("animals.animal[0].name", equalTo("Skye"))
            .body("animals.animal[0].species", equalTo("dog"))
            .body("animals.animal[0].legs", equalTo(4))
            .body("animals.animal[3].name", equalTo("Sunny"))
            .body("animals.animal[3].legs", equalTo(2))
            .body("animals.animal[4].species", equalTo("goldfish"))
            .body("animals.animal[4].legs", equalTo(0));
        // @formatter:on
    }
}
