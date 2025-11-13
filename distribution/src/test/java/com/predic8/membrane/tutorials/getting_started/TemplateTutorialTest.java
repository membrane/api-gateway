package com.predic8.membrane.tutorials.getting_started;

import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;

public class TemplateTutorialTest extends AbstractGettingStartedTutorialTest{

    @Override
    protected String getTutorialYaml() {
        return "70-Template.yaml";
    }

    @Test
    void membraneTemplateFoo() {
        // @formatter:off
        given()
        .when()
            .get("http://localhost:2000/foo")
        .then()
            .statusCode(200)
            .contentType("text/plain")
            .body(containsString("Method: GET"))
            .body(containsString("Path: /foo"))
            .body(containsString("Date:"));
        // @formatter:on
    }

}
