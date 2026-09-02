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

package com.predic8.membrane.core.interceptor.json;

import com.fasterxml.jackson.core.JsonParseException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;

import static com.google.common.base.Strings.repeat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Limit enforcement, checked directly on the scanner. The HTTP-level contract (status codes,
 * ProblemDetails, multipart part naming) is covered by {@link JsonProtectionInterceptorTest}.
 */
public class JsonProtectionScannerTest {

    private static final JsonLimits LIMITS =
            new JsonLimits(4096, 10240, 10, 20, 10, 10, 2048, true);

    private static final JsonProtectionScanner scanner = new JsonProtectionScanner(LIMITS);

    @Test
    void wellFormedDocumentWithinLimitsPasses() throws Exception {
        scan("""
                {"a":"b","c":[1,2,{"d":null}]}""");
    }

    @Test
    void maxDepthCountsObjectsAndArraysAlike() {
        assertEquals("Exceeded maxDepth.", violation(repeat("{\"a\":", 11) + "1" + repeat("}", 11)));
        assertEquals("Exceeded maxDepth.", violation(repeat("[", 11) + "1" + repeat("]", 11)));
    }

    @Test
    void justNotTooDeepPasses() throws Exception {
        scan(repeat("{\"a\":", 10) + "1" + repeat("}", 10));
    }

    @Test
    void maxObjectSizeIsPerObject() {
        assertEquals("Exceeded maxObjectSize.", violation(object(11)));
        assertDoesNotThrow(() -> scan(object(10)));
    }

    @Test
    void maxArraySizeIsExceeded() {
        assertEquals("Exceeded maxArraySize.", violation("[" + repeat("1,", 2048) + "1]"));
    }

    @Test
    void maxStringLengthIsExceeded() {
        assertEquals("Exceeded maxStringLength.", violation("{\"a\":\"" + repeat("x", 21) + "\"}"));
    }

    @Test
    void maxKeyLengthIsExceeded() {
        assertEquals("Exceeded maxKeyLength.", violation("{\"" + repeat("k", 11) + "\":1}"));
    }

    @Test
    void maxTokensIsExceeded() {
        // Room in the array so that only the token count can trip.
        JsonProtectionScanner s = new JsonProtectionScanner(
                new JsonLimits(4096, 10240, 10, 20, 10, 10, 100000, true));
        var e = assertThrows(JsonProtectionException.class,
                () -> s.scan(stream("[" + repeat("1,", 4096) + "1]")));
        assertEquals("Exceeded maxTokens.", e.getMessage());
    }

    @Test
    void maxSizeIsExceeded() {
        // Few tokens, but a lot of bytes: only maxSize can catch this.
        JsonProtectionScanner tiny = new JsonProtectionScanner(
                new JsonLimits(4096, 32, 10, 262144, 256, 10, 2048, true));
        var e = assertThrows(JsonProtectionException.class,
                () -> tiny.scan(stream("{\"a\":\"" + repeat("x", 200) + "\"}")));
        assertEquals("Exceeded maxSize.", e.getMessage());
    }

    @Test
    void protoKeyIsBlockedOnlyWhileBlockProtoIsOn() throws Exception {
        assertEquals("__proto__ found as key.", violation("{\"__proto__\":1}"));

        JsonProtectionScanner permissive = new JsonProtectionScanner(
                new JsonLimits(4096, 10240, 10, 20, 10, 10, 2048, false));
        permissive.scan(stream("{\"__proto__\":1}"));
    }

    @Test
    void duplicateKeysAreRejected() {
        assertThrows(JsonParseException.class, () -> scan("""
                {"a":1,"a":2}"""));
    }

    @Test
    void malformedDocumentIsRejected() {
        assertThrows(JsonParseException.class, () -> scan("{\"a\":"));
    }

    @Test
    void violationReportsTheLocationItWasDetectedAt() {
        var e = assertThrows(JsonProtectionException.class, () -> scan("{\"a\":\"" + repeat("x", 21) + "\"}"));
        assertEquals(1, e.getLine());
        assertTrue(e.getCol() > 1, "column should point into the document, was " + e.getCol());
    }

    /**
     * A key is a string too, so the stricter of the two limits has to win regardless of which one
     * the user configured lower.
     */
    @Test
    void keyLengthIsClampedToStringLength() {
        assertEquals(5, new JsonLimits(1, 1, 1, 5, 100, 1, 1, true).maxKeyLength());
        assertEquals(5, new JsonLimits(1, 1, 1, 100, 5, 1, 1, true).maxKeyLength());
    }

    private static String object(int members) {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < members; i++)
            sb.append(i > 0 ? "," : "").append("\"k").append(i).append("\":1");
        return sb.append("}").toString();
    }

    private static String violation(String json) {
        return assertThrows(JsonProtectionException.class, () -> scan(json)).getMessage();
    }

    private static void scan(String json) throws Exception {
        scanner.scan(stream(json));
    }

    private static ByteArrayInputStream stream(String json) {
        return new ByteArrayInputStream(json.getBytes(UTF_8));
    }
}
