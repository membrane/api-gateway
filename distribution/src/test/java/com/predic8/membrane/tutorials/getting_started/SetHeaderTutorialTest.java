package com.predic8.membrane.tutorials.getting_started;

import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;

public class SetHeaderTutorialTest extends AbstractGettingStartedTutorialTest{

    @Override
    protected String getTutorialYaml() {
        return "60-SetHeader.yaml";
    }

    @Test
    void get() {
        // @formatter:off
        given()
        .when()
            .get("http://localhost:2000")
        .then()
            .statusCode(200)
            .header("X-Powered-By", equalTo("Membrane"))
            .header("X-Method", equalTo("GET"));
        // @formatter:on
    }

    @Test
    void post() {
        // @formatter:off
        given()
        .when()
            .post("http://localhost:2000")
        .then()
            .statusCode(200)
            .header("X-Powered-By", equalTo("Membrane"))
            .header("X-Method", equalTo("POST"));
        // @formatter:on
    }

}
