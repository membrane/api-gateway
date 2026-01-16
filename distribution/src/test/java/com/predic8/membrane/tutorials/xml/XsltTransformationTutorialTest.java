package com.predic8.membrane.tutorials.xml;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.XML;
import static org.hamcrest.Matchers.*;

public class XsltTransformationTutorialTest extends AbstractXmlTutorialTest{
    @Override
    protected String getTutorialYaml() {
        return "40-XSLT-transformation.yaml";
    }

    @Test
    void xsltTransformsXml() throws IOException {
        // @formatter:off
        given()
            .body(readFileFromBaseDir("books.xml"))
            .contentType(XML)
        .when()
            .post("http://localhost:2000")
        .then()
            .statusCode(200)
            .contentType(XML)
            .body(startsWith("<?xml"))
            .body(containsString("<categories>"))
            .body(containsString("</categories>"))
            .body(containsString("<category name=\""))
            .body(containsString("<title>"))
            .body(anyOf(
                    containsString("Software Engineering"),
                    containsString("Clean Code"),
                    containsString("Design Patterns")
            ));
        // @formatter:on
    }
}
