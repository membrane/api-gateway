package com.predic8.membrane.tutorials.xml;

import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class XsdSchemaValidationTutorialTest extends AbstractXmlTutorialTest{
    @Override
    protected String getTutorialYaml() {
        return "50-XSD-Schema-validation.yaml";
    }

    @Test
    void validXmlPassesValidation() {
        // @formatter:off
        given()
            .body("book-valid.xml")
            .contentType("text/xml")
        .when()
            .post("http://localhost:2000")
        .then()
            .statusCode(200)
            .body(equalTo("Ok"));
        // @formatter:on
    }

    @Test
    void invalidXmlFailsValidation() {
        // @formatter:off
        given()
            .body("book-invalid.xml")
            .contentType("text/xml")
        .when()
            .post("http://localhost:2000")
        .then()
            .statusCode(anyOf(is(400), is(500)))
            .body(anyOf(
                    containsStringIgnoringCase("schema"),
                    containsStringIgnoringCase("xsd"),
                    containsStringIgnoringCase("validation"),
                    containsStringIgnoringCase("valid")
            ));
        // @formatter:on
    }
}
