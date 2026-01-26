package com.predic8.membrane.tutorials.orchestration;

import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

public class OrchestrationTutorialTest extends AbstractOrchestrationTutorialTest {

    @Override
    protected String getTutorialYaml() {
        return "10-Orchestration.yaml";
    }

    @Test
    void books_areReturnedById() {
        // @formatter:off
        given()
        .when()
            .get("http://localhost:2000/books/{id}", "OL29474405M")
        .then()
            .statusCode(200)
            .body("title", equalTo("So Long, and Thanks for All the Fish"))
            .body("author", equalTo("Douglas Adams"));

        given()
        .when()
            .get("http://localhost:2000/books/{id}", "OL26333978M")
        .then()
            .statusCode(200)
            .body("title", equalTo("Foucault's pendulum"))
            .body("author", equalTo("Umberto Eco"));
        // @formatter:on
    }

}
