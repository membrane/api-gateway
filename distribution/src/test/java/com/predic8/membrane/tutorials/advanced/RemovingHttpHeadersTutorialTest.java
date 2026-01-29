package com.predic8.membrane.tutorials.advanced;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RemovingHttpHeadersTutorialTest extends AbstractAdvancedTutorialTest {

    @Override
    protected String getTutorialYaml() {
        return "80-Removing-HTTP-Headers.yaml";
    }

    @Test
    void logsBeforeAndAfterHeaders() {
        synchronized (System.out) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PrintStream original = System.out;
            System.setOut(new PrintStream(out));

            try {
                // @formatter:off
                given()
                    .header("X-Foo", "1")
                    .header("X-bar", "2")
                .when()
                    .get("http://localhost:2000")
                .then()
                    .statusCode(200);
                // @formatter:on
            } finally {
                System.setOut(original);
            }

            String console = out.toString();

            int beforeIdx = lastIndexOfIgnoreCase(console, "Before:");
            int afterIdx  = lastIndexOfIgnoreCase(console, "After:");
            assertTrue(beforeIdx >= 0, "Missing 'Before:' log.");
            assertTrue(afterIdx > beforeIdx, "Missing 'After:' log or wrong order.");

            String before = console.substring(beforeIdx, afterIdx);
            String after  = console.substring(afterIdx);

            assertTrue(containsIgnoreCase(before, "X-Foo: 1"));
            assertTrue(containsIgnoreCase(before, "X-bar: 2"));

            assertTrue(containsIgnoreCase(after, "Host:"));
            assertTrue(containsIgnoreCase(after, "Accept:"));
            assertTrue(containsIgnoreCase(after, "X-Foo: 1"));

            assertFalse(containsIgnoreCase(after, "X-bar:"));
            assertFalse(containsIgnoreCase(after, "User-Agent:"));
            assertFalse(containsIgnoreCase(after, "X-Forwarded-"));
        }
    }

    private static boolean containsIgnoreCase(String haystack, String needle) {
        return haystack.toLowerCase().contains(needle.toLowerCase());
    }

    private static int lastIndexOfIgnoreCase(String haystack, String needle) {
        return haystack.toLowerCase().lastIndexOf(needle.toLowerCase());
    }

}
