package com.predic8.membrane.tutorials.getting_started;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MessageFlowTutorialTest extends AbstractGettingStartedTutorialTest{

    @Override
    protected String getTutorialYaml() {
        return "20-Message-Flow.yaml";
    }

    @Test
    void flowLogs() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream original = System.out;
        System.setOut(new PrintStream(out));

        try {
            // @formatter:off
            given()
            .when()
                .get("http://localhost:2000")
            .then()
                .statusCode(200)
                .body(containsString("Shop API Showcase"));
            // @formatter:on
        } finally {
            System.setOut(original);
        }

        String console = out.toString();
        assertTrue(console.contains("INFO LogInterceptor"));
        assertTrue(console.contains("Path / Status 0"));
        assertTrue(console.contains("Path / Status 200"));
    }

}
