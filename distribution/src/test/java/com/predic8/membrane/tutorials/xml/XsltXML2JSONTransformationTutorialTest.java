package com.predic8.membrane.tutorials.xml;

import org.junit.jupiter.api.*;

import java.io.*;

import static io.restassured.RestAssured.*;
import static io.restassured.http.ContentType.*;
import static org.hamcrest.Matchers.*;

public class XsltXML2JSONTransformationTutorialTest extends AbstractXmlTutorialTest{
    @Override
    protected String getTutorialYaml() {
        return "35-XSLT-Transformation-to-json.yaml";
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
            .contentType(JSON)
            .body("books.size()", greaterThan(0))
            .body("books[0].year", equalTo("1975"));
        // @formatter:on
    }
}
