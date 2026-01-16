package com.predic8.membrane.tutorials.xml;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.XML;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;

public class XPathTutorialTest extends AbstractXmlTutorialTest {
    @Override
    protected String getTutorialYaml() {
        return "30-XPath.yaml";
    }

    @Test
    void xpathExtractsPropertiesAndSetsHeader() throws IOException {
        // @formatter:off
        given()
            .body(readFileFromBaseDir("animals.xml"))
            .contentType(XML)
        .when()
            .post("http://localhost:2000")
        .then()
            .statusCode(200)
            .contentType(XML)
            .body(allOf(
                    containsString("Names:"),
                    containsString("Skye"),
                    containsString("Molly"),
                    containsString("Biscuit"),
                    containsString("Sunny"),
                    containsString("Bubbles"),
                    containsString("Name: Skye")
            ));
        // @formatter:on
    }
}
