package com.predic8.membrane.tutorials.advanced;

import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;

public class ChooseTutorialTest extends AbstractAdvancedTutorialTest {

    @Override
    protected String getTutorialYaml() {
        return "65-Choose.yaml";
    }


    @Test
    void withoutHeader_returns400() {
        // @formatter:off
        given()
        .when()
            .get("http://localhost:2000/")
        .then()
            .statusCode(400);
        // @formatter:on
    }

    @Test
    void withXFoo_returns200() {
        // @formatter:off
        given()
            .header("X-Foo", "1")
        .when()
            .get("http://localhost:2000/")
        .then()
            .statusCode(200);
        // @formatter:on
    }

    @Test
    void withXBar_returns300_andConnectionClosed() {
        // @formatter:off
        given()
            .header("X-Bar", "1")
        .when()
            .get("http://localhost:2000/")
        .then()
            .statusCode(300)
            .header("Connection", "close");
        // @formatter:on
    }

}
