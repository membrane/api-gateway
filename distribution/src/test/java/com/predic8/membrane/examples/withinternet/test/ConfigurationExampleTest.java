package com.predic8.membrane.examples.withinternet.test;

import com.predic8.membrane.examples.util.AbstractSampleMembraneStartStopTestcase;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConfigurationExampleTest extends AbstractSampleMembraneStartStopTestcase {

    @Override
    protected String getExampleDirName() {
        return "configuration";
    }

    @Test
    void consoleLogs() {
        synchronized (System.out) {
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
            assertTrue(console.contains("==== REQUEST  ==="));
            assertTrue(console.contains("==== RESPONSE  ==="));
        }
    }

}
