package com.predic8.membrane.tutorials.getting_started;

import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;

public class ShortCircuitTutorialTest extends AbstractGettingStartedTutorialTest{

    @Override
    protected String getTutorialYaml() {
        return "50-Short-Circuit.yaml";
    }

    @Test
    void statusCode() {
        // @formatter:off
        given()
        .when()
            .get("http://localhost:2000")
        .then()
            .statusCode(200);
        // @formatter:on
    }

    @Test
    void body() {
        // @formatter:off
        given()
            .body("Bonjour")
            .contentType("application/x-www-form-urlencoded")
        .when()
            .post("http://localhost:2000")
        .then()
            .statusCode(200)
            .body(containsString("Bonjour"));
        // @formatter:on
    }
}
