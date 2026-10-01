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

package com.predic8.membrane.tutorials.operation;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static java.lang.System.nanoTime;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Verifies tutorial step 80-Byte-Stream-Logging.yaml: with log4j2-traffic.xml the raw bytes of
 * both the client and the backend connection end up in traffic.log.
 */
public class ByteStreamLoggingTutorialTest extends AbstractOperationTutorialTest {

    @Override
    protected String getTutorialYaml() {
        return "80-Byte-Stream-Logging.yaml";
    }

    @Override
    protected Map<String, String> getEnvs() {
        return Map.of("JAVA_OPTS", "-Dlog4j.configurationFile=" + baseDir.toPath().resolve("log4j2-traffic.xml").toAbsolutePath());
    }

    @Test
    void writesWireTrafficToFile() throws Exception {
        // @formatter:off
        given()
        .when()
            .get("http://localhost:2000")
        .then()
            .statusCode(200)
            .body(containsString("apibin"));
        // @formatter:on

        waitForLogContaining(baseDir.toPath().resolve("traffic.log"),
                " in] [ ", " out] [ ", "GET / HTTP/1.1", "Host: apibin.io:443", "\"api\": \"apibin\"");
    }

    private static void waitForLogContaining(Path file, String... parts) throws Exception {
        long deadline = nanoTime() + SECONDS.toNanos(10);
        String content = "";
        while (nanoTime() < deadline) {
            if (Files.exists(file)) {
                content = Files.readString(file);
                if (containsAll(content, parts))
                    return;
            }
            Thread.sleep(100);
        }
        fail("traffic.log does not contain all of the expected parts. Content:\n" + content);
    }

    private static boolean containsAll(String content, String... parts) {
        for (String part : parts) {
            if (!content.contains(part))
                return false;
        }
        return true;
    }
}
