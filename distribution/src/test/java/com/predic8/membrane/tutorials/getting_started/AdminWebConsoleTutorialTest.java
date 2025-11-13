package com.predic8.membrane.tutorials.getting_started;

import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;

public class AdminWebConsoleTutorialTest extends AbstractGettingStartedTutorialTest{

    @Override
    protected String getTutorialYaml() {
        return "45-Admin-Web-Console.yaml";
    }

    @Test
    void adminConsole() {
        // @formatter:off
        given()
            .auth().preemptive().basic("admin", "admin")
        .when()
            .get("http://localhost:9000")
        .then()
            .statusCode(200)
            .body(containsString("<!DOCTYPE html>"));
        // @formatter:on
    }

}
