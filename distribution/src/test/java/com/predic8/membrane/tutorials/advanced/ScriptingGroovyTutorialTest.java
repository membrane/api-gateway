package com.predic8.membrane.tutorials.advanced;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertTrue;


public class ScriptingGroovyTutorialTest extends AbstractAdvancedTutorialTest {

    @Override
    protected String getTutorialYaml() {
        return "70-Scripting-Groovy.yaml";
    }

    @Test
    void groovyEndpointLogs() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream original = System.out;
        System.setOut(new PrintStream(out));

        try {
            // @formatter:off
            given()
            .when()
                .get("http://localhost:2000/groovy")
            .then()
                .statusCode(200);
            // @formatter:on
        } finally {
            System.setOut(original);
        }

        String console = out.toString();
        assertTrue(console.contains("I'm executed in the REQUEST flow"));
        assertTrue(console.contains("I'm executed in the RESPONSE flow"));
    }

    @Test
    void randomEndpoint() {
        // @formatter:off
        given()
        .when()
            .get("http://localhost:2000/random")
        .then()
            .body(notNullValue());
        // @formatter:on
    }

    @Test
    void groovyCustomResponse() {
        // @formatter:off
        given()
        .when()
            .get("http://localhost:2000/response")
        .then()
            .statusCode(200)
            .contentType("application/json")
            .header("X-Foo", "bar")
            .body("success", equalTo(true));
        // @formatter:on
    }

}
