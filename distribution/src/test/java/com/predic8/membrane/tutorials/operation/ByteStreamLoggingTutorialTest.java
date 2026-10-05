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

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.restassured.RestAssured.given;
import static java.lang.System.nanoTime;
import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Verifies tutorial step 80-Byte-Stream-Logging.yaml: with log4j2-traffic.xml the raw bytes of
 * both the client and the backend connection end up in traffic.log.
 */
public class ByteStreamLoggingTutorialTest extends AbstractOperationTutorialTest {

    /**
     * Matches the head of an entry, e.g. "[membrane=>backend 899486797] [ 71 69 84 ]".
     */
    private static final Pattern ENTRY = Pattern.compile("\\[((?:client|membrane|backend)=>(?:client|membrane|backend) \\d+)] \\[ ((?:-?\\d+ )*)]");

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

        waitForStreams(baseDir.toPath().resolve("traffic.log"), streams ->
                anyStream(streams, "client=>membrane", "GET / HTTP/1.1")
                && anyStream(streams, "membrane=>backend", "GET / HTTP/1.1", "Host: apibin.io:443")
                && anyStream(streams, "backend=>membrane", "\"api\": \"apibin\"")
                && anyStream(streams, "membrane=>client", "\"api\": \"apibin\""));
    }

    /**
     * A single read or write may be logged in several entries (down to one byte each), so the
     * expected text is searched in the bytes of all entries of one connection and direction.
     */
    private static void waitForStreams(Path file, Predicate<Map<String, String>> expected) throws Exception {
        long deadline = nanoTime() + SECONDS.toNanos(10);
        String content = "";
        while (nanoTime() < deadline) {
            if (Files.exists(file)) {
                content = Files.readString(file);
                if (expected.test(reconstructStreams(content)))
                    return;
            }
            Thread.sleep(100);
        }
        fail("traffic.log does not contain all of the expected parts. Content:\n" + content);
    }

    /**
     * @return key "direction connection", e.g. "membrane=>backend 899486797", mapped to the concatenated bytes
     */
    private static Map<String, String> reconstructStreams(String log) {
        Map<String, ByteArrayOutputStream> streams = new LinkedHashMap<>();
        Matcher m = ENTRY.matcher(log);
        while (m.find()) {
            ByteArrayOutputStream stream = streams.computeIfAbsent(m.group(1), k -> new ByteArrayOutputStream());
            for (String b : m.group(2).trim().split(" ")) {
                if (!b.isEmpty())
                    stream.write(Byte.parseByte(b));
            }
        }
        Map<String, String> result = new LinkedHashMap<>();
        streams.forEach((key, bytes) -> result.put(key, bytes.toString(ISO_8859_1)));
        return result;
    }

    private static boolean anyStream(Map<String, String> streams, String direction, String... parts) {
        return streams.entrySet().stream()
                .filter(e -> e.getKey().startsWith(direction + " "))
                .anyMatch(e -> Arrays.stream(parts).allMatch(e.getValue()::contains));
    }
}
