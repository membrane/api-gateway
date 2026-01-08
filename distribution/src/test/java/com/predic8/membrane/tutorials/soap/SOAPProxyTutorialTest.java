package com.predic8.membrane.tutorials.soap;

import org.junit.jupiter.api.*;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

public class SOAPProxyTutorialTest extends AbstractSOAPTutorialTest {

    @Override
    protected String getTutorialYaml() {
        return "20-SOAPProxy.yaml";
    }

    @Test
    void adminConsole() {
        // @formatter:off
        given()
        .when()
            .get("http://localhost:2000/city-service?wsdl")
        .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body(containsString("definitions"));
        // @formatter:on
    }

}
