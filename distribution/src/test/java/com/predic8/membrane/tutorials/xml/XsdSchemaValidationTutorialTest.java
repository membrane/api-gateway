package com.predic8.membrane.tutorials.xml;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.XML;
import static org.hamcrest.Matchers.*;

public class XsdSchemaValidationTutorialTest extends AbstractXmlTutorialTest{
    @Override
    protected String getTutorialYaml() {
        return "50-XSD-Schema-validation.yaml";
    }

    @Test
    void validXmlPassesValidation() throws IOException {
        // @formatter:off
        given()
            .body(readFileFromBaseDir("book-valid.xml"))
            .contentType(XML)
        .when()
            .post("http://localhost:2000")
        .then()
            .statusCode(200)
            .body(equalTo("Ok"));
        // @formatter:on
    }

    @Test
    void invalidXmlFailsValidation() throws IOException {
        // @formatter:off
        given()
            .contentType("application/x-www-form-urlencoded")
            .body(readFileFromBaseDir("book-invalid.xml"))
        .when()
            .post("http://localhost:2000")
        .then()
            .statusCode(400)
            .body("title", equalTo("XML message validation failed"))
            .body("type", equalTo("https://membrane-api.io/problems/user"))
            .body("status", equalTo(400))
            .body("see", equalTo("https://membrane-api.io/problems/user/xml-schema-validator"))
            .body("attention", containsString("development mode"))
            .body("validation.size()", equalTo(1))
            .body("validation[0].message", allOf(
                    containsString("year"),
                    containsStringIgnoringCase("not valid")
            ))
            .body("validation[0].line", equalTo(8))
            .body("validation[0].column", equalTo(38));
        // @formatter:on
    }
}
