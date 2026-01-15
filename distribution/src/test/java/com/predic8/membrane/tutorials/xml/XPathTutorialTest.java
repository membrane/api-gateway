package com.predic8.membrane.tutorials.xml;

import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class XPathTutorialTest extends AbstractXmlTutorialTest{
    @Override
    protected String getTutorialYaml() {
        return "30-XPath.yaml";
    }

    @Test
    void xpathExtractsPropertiesAndSetsHeader() {
        // @formatter:off
        given()
            .body("animals.xml")
            .contentType("text/xml")
        .when()
            .post("http://localhost:2000")
        .then()
            .statusCode(200)
            .contentType(anyOf(containsString("xml"), containsString("application/xml"), containsString("text/xml")))
            .header("X-Sunny", allOf(containsString("Sunny is a"), not(emptyOrNullString())))
            .body(allOf(containsString("Names:"), containsString("Name:")));
        // @formatter:on
    }
}
