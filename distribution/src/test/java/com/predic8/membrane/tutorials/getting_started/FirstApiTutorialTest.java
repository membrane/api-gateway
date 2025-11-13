package com.predic8.membrane.tutorials.getting_started;

import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;

public class FirstApiTutorialTest extends AbstractGettingStartedTutorialTest {

    @Override
    protected String getTutorialYaml() {
        return "00-First-API.yaml";
    }

    @Test
    void apiShowcase() {
        // @formatter:off
        given()
        .when()
            .get("http://localhost:2000")
        .then()
            .statusCode(200)
            .body(containsString("Shop API Showcase"));
        // @formatter:on
    }

}