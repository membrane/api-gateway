package com.predic8.membrane.tutorials.xml;

import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class XsltTransformationTutorialTest extends AbstractXmlTutorialTest{
    @Override
    protected String getTutorialYaml() {
        return "40-XSLT-transformation.yaml";
    }

    @Test
    void xsltTransformsXml() {
        // @formatter:off
        given()
            .body("books.xml")
            .contentType("text/xml")
        .when()
            .post("http://localhost:2000")
        .then()
            .statusCode(200)
            .contentType(anyOf(containsString("xml"), containsString("application/xml"), containsString("text/xml")))
            .body(anyOf(
                    startsWith("<?xml"),
                    startsWith("<")
            ))
            .body(anyOf(
                    containsString("category"),
                    containsString("Category"),
                    containsString("book"),
                    containsString("Book")
            ));
        // @formatter:on
    }
}
