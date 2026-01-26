package com.predic8.membrane.tutorials.advanced;

import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;

public class IfElseTutorialTest extends AbstractAdvancedTutorialTest {

    @Override
    protected String getTutorialYaml() {
        return "61-If-Else.yaml";
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
    void withHeader_returns200() {
        // @formatter:off
        given()
            .header("X-Foo", "1")
        .when()
            .get("http://localhost:2000/")
        .then()
            .statusCode(200);
        // @formatter:on
    }

}
