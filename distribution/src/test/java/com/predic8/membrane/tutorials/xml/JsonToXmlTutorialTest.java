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
            .body("zoo.number", equalTo("2"))
            .body("zoo.animals.array.item.size()", greaterThanOrEqualTo(2))
            .body("zoo.animals.array.item.name", hasItems("Skye", "Molly"))
            .body("zoo.animals.array.item.species", hasItems("dog", "cat"))
            .body("zoo.animals.array.item.find { it.name == 'Skye' }.legs", equalTo("4"));
        // @formatter:on
    }
}
