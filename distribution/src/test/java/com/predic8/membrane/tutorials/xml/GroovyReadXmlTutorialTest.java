/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.tutorials.xml;

import com.predic8.membrane.examples.util.ConsoleWatcher;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static io.restassured.http.ContentType.XML;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroovyReadXmlTutorialTest extends AbstractXmlTutorialTest {
    @Override
    protected String getTutorialYaml() {
        return "70-Groovy-Read-XML.yaml";
    }

    @Test
    void keepsOnlyRecentBooks() throws IOException {
        // @formatter:off
        given()
            .body(readFileFromBaseDir("books.xml"))
            .contentType(XML)
        .when()
            .post("http://localhost:2000")
        .then()
            .statusCode(200)
            .contentType(JSON)
            .body("$", contains(
                "Clean Code",
                "Patterns of Enterprise Application Architecture",
                "The Art of Unix Programming",
                "Site Reliability Engineering",
                "The Phoenix Project"));
        // @formatter:on
    }

    @Test
    void logsQueryResults() throws IOException, InterruptedException {
        final var bookCountLogged = new CountDownLatch(1);
        final var authorsLogged = new CountDownLatch(1);
        final ConsoleWatcher watcher = (error, line) -> {
            if (line.contains("Number of books: 10"))
                bookCountLogged.countDown();
            if (line.contains("Authors of book 5: [Erich Gamma, Richard Helm, Ralph Johnson, John Vlissides]"))
                authorsLogged.countDown();
        };

        process.addConsoleWatcher(watcher);
        try {
            // @formatter:off
            given()
                .body(readFileFromBaseDir("books.xml"))
                .contentType(XML)
            .when()
                .post("http://localhost:2000")
            .then()
                .statusCode(200);
            // @formatter:on

            // Console output is relayed asynchronously and can arrive after the response.
            assertTrue(bookCountLogged.await(5, SECONDS));
            assertTrue(authorsLogged.await(5, SECONDS));
        } finally {
            process.removeConsoleWatcher(watcher);
        }
    }
}
