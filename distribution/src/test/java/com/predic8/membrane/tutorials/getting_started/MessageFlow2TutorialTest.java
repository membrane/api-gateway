package com.predic8.membrane.tutorials.getting_started;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MessageFlow2TutorialTest extends AbstractGettingStartedTutorialTest{

    @Override
    protected String getTutorialYaml() {
        return "30-Message-Flow2.yaml";
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
        System.out.println(console);
        assertTrue(console.contains("TODO"));
        assertTrue(console.contains("TODO"));
        assertTrue(console.contains("TODO"));
    }

    // TODO Example currently not working. Fix yaml issues, then adjust test

}
