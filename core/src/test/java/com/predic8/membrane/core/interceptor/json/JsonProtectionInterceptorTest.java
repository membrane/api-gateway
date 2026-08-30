/* Copyright 2023 predic8 GmbH, www.predic8.com

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

import com.predic8.membrane.core.exceptions.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.router.*;
import org.junit.jupiter.api.*;

import static com.google.common.base.Strings.*;
import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.interceptor.json.JsonProtectionInterceptor.OtherContentTypes.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.util.ProblemDetailsTestUtil.*;
import static org.junit.jupiter.api.Assertions.*;

public class JsonProtectionInterceptorTest {
    static JsonProtectionInterceptor jpiProd;
    static JsonProtectionInterceptor jpiDev;

    private static JsonProtectionInterceptor buildJPI(boolean prod) {
        DefaultRouter router = new DefaultRouter();
        router.getConfiguration().setProduction(prod);
        JsonProtectionInterceptor jpi = new JsonProtectionInterceptor();

        jpi.setMaxTokens(4096);
        jpi.setMaxSize(10240);
        jpi.setMaxDepth(10);
        jpi.setMaxStringLength(20);
        jpi.setMaxKeyLength(10);
        jpi.setMaxObjectSize(10);
        jpi.setMaxArraySize(2048);
        jpi.setBlockProto(true);

        jpi.init(router);
        return jpi;
    }

    @BeforeEach
    public void setup() {
        jpiProd = buildJPI(true);
        jpiDev = buildJPI(false);
    }

    @Test
    public void ok() throws Exception {
        send("""
                {}""",
                CONTINUE);
    }

    @Test
    public void ok2() throws Exception {
        send("""
                {"a":"b"}""",
                CONTINUE);
    }

    @Test
    void duplicateKey() throws Exception {
        send("""
                {"a":1,"a":2}""",
                RETURN,
                1,
                11,
                "Duplicate field");
    }

    @Test
    public void malformed() throws Exception {
        send("""
                {""",
                RETURN,
                1,
                2,
                "close marker for Object");
    }

    @Test
    public void empty() throws Exception {
        send("", CONTINUE);
    }

    @Test
    public void tooLong() throws Exception {
        send("[" + repeat("\"0123456\",", 1024) + "\"x\"]",
                RETURN,
                1,
                8003,
                "Exceeded maxSize.");
    }

    @Test
    public void justNotTooLong() throws Exception {
        send("[" + repeat("\"0123456\",", 1023) + "\"x\"]",
                CONTINUE);
    }

    @Test
    public void tooDeep() throws Exception {
        send(repeat("{\"a\":", 11) + "1" + repeat("}", 11),
                RETURN,
                1,
                52,
                "Exceeded maxDepth.");
    }

    @Test
    void tooDeepArray() throws Exception {
        // Prevent from kicking in
        jpiDev.setMaxArraySize(1000);
        jpiProd.setMaxArraySize(1000);

        send(repeat("[", 11) + "1" + repeat("]", 11),
                RETURN,
                1,
                12,
                "Exceeded maxDepth.");
    }

    @Test
    public void justNotTooDeep() throws Exception {
        send(repeat("{\"a\":", 10) + "1" + repeat("}", 10),
                CONTINUE);
    }

    @Test
    public void stringTooLong() throws Exception {
        send("[\"" + repeat("1", 21) + "\"]",
                RETURN,
                1,
                25,
                "Exceeded maxStringLength.");
    }

    @Test
    public void stringJustNotTooLong() throws Exception {
        send("[\"" + repeat("1", 20) + "\"]",
                CONTINUE);
    }

    @Test
    public void keyTooLong() throws Exception {
        send("{\"01234567890\": \"" + repeat("1", 20) + "\"}",
                RETURN,
                1,
                18,
                "Exceeded maxKeyLength.");
    }

    @Test
    public void keyTooLong2() throws Exception {
        send("{\"0123456789\": { \"01234567890\": \"" + repeat("1", 20) + "\"} }",
                RETURN,
                1,
                34,
                "Exceeded maxKeyLength.");
    }

    @Test
    public void keyTooLong3() throws Exception {
        send("{\"0123456789\": [ { \"01234567890\": \"" + repeat("1", 20) + "\"} ] }",
                RETURN,
                1,
                36,
                "Exceeded maxKeyLength.");
    }

    @Test
    public void keyNotTooLong() throws Exception {
        send("{\"0123456789\": [ { \"0123456789\": \"" + repeat("1", 20) + "\"} ] }",
                CONTINUE);
    }

    @Test
    public void objectTooLarge() throws Exception {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < 11; i++) {
            if (i != 0)
                sb.append(",");
            sb.append("\"").append(i).append("\": 1");
        }
        sb.append("}");
        send(sb.toString(),
                RETURN,
                1,
                79,
                "Exceeded maxObjectSize.");
    }

    @Test
    public void objectJustNotTooLarge() throws Exception {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < 10; i++) {
            if (i != 0)
                sb.append(",");
            sb.append("\"").append(i).append("\": 1");
        }
        sb.append("}");
        send(sb.toString(),
                CONTINUE);
    }

    @Test
    public void arrayTooLarge() throws Exception {
        send("[" + repeat("1,", 2048) + "1]",
                RETURN,
                1,
                4099,
                "Exceeded maxArraySize.");
    }

    @Test
    public void arrayJustNotTooLarge() throws Exception {
        send("[" + repeat("1,", 2047) + "1]",
                CONTINUE);
    }

    @Test
    public void tooManyTokens() throws Exception {
        send("[" + repeat("1,", 2047) + "[" + repeat("1,", 2047) + "1]" + "]",
                RETURN,
                1,
                8192,
                "Exceeded maxTokens.");
    }

    @Test
    public void justNotTooManyTokens() throws Exception {
        send("[" + repeat("1,", 2045) + "[" + repeat("1,", 2045) + "1]" + "]",
                CONTINUE);
    }

    @Test
    public void protoBlocked() throws Exception {
        send("{\"__proto__\": {}}",
                RETURN,
                1,
                16,
                "__proto__ found as key.");
    }

    // --- Multipart / attachments -------------------------------------------------------------

    @Test
    void jsonPartOfMultipartIsInspected() throws Exception {
        var exc = multipartExchange(part("data", APPLICATION_JSON, deeplyNested()));

        assertEquals(RETURN, jpiDev.handleRequest(exc));
        var pd = parse(exc.getResponse());
        assertTrue(pd.getDetail().contains("Exceeded maxDepth."), pd.getDetail());
        assertTrue(pd.getDetail().contains("data"), "should name the offending part: " + pd.getDetail());
    }

    @Test
    void benignMultipartPasses() throws Exception {
        var exc = multipartExchange(part("data", APPLICATION_JSON, "{\"a\":\"b\"}"));

        assertEquals(CONTINUE, jpiDev.handleRequest(exc));
        assertNull(exc.getResponse());
    }

    @Test
    void everyJsonPartIsInspected() throws Exception {
        var exc = multipartExchange(
                part("first", APPLICATION_JSON, "{\"a\":\"b\"}"),
                part("second", APPLICATION_JSON, deeplyNested()));

        assertEquals(RETURN, jpiDev.handleRequest(exc));
        assertTrue(parse(exc.getResponse()).getDetail().contains("second"));
    }

    @Test
    void nonJsonPartIsRejectedByDefault() throws Exception {
        var exc = multipartExchange(part("logo", "image/png", "\u0089PNG"));

        assertEquals(RETURN, jpiDev.handleRequest(exc));
        assertTrue(parse(exc.getResponse()).getDetail().contains("is not JSON"));
    }

    @Test
    void nonJsonPartIsSkippedWhenConfigured() throws Exception {
        jpiDev.setOtherContentTypes(SKIP);
        var exc = multipartExchange(
                part("logo", "image/png", "\u0089PNG"),
                part("data", APPLICATION_JSON, "{\"a\":\"b\"}"));

        assertEquals(CONTINUE, jpiDev.handleRequest(exc));
        assertNull(exc.getResponse());
    }

    @Test
    void multipartBodyIsNotModified() throws Exception {
        jpiDev.setOtherContentTypes(SKIP);
        var exc = multipartExchange(part("logo", "image/png", "\u0089PNG"));
        byte[] before = exc.getRequest().getBody().getContent();

        assertEquals(CONTINUE, jpiDev.handleRequest(exc));
        assertArrayEquals(before, exc.getRequest().getBody().getContent());
    }

    /**
     * A part without a Content-Type defaults to text/plain, so it is not JSON - unlike a whole body
     * without a Content-Type, which is still parsed (see {@link #bodyWithoutContentTypeIsStillParsed()}).
     */
    @Test
    void partWithoutContentTypeIsNotJson() throws Exception {
        jpiDev.setOtherContentTypes(SKIP);
        var exc = multipartExchange(part("field", null, deeplyNested()));

        assertEquals(CONTINUE, jpiDev.handleRequest(exc));
    }

    @Test
    void bodyWithoutContentTypeIsStillParsed() throws Exception {
        var exc = Request.post("/").body(deeplyNested()).buildExchange();

        assertEquals(RETURN, jpiDev.handleRequest(exc));
        assertTrue(parse(exc.getResponse()).getDetail().contains("Exceeded maxDepth."));
    }

    @Test
    void nonJsonBodyIsSkippedWhenConfigured() throws Exception {
        jpiDev.setOtherContentTypes(SKIP);
        var exc = Request.post("/").contentType("image/png").body("\u0089PNG").buildExchange();

        assertEquals(CONTINUE, jpiDev.handleRequest(exc));
        assertNull(exc.getResponse());
    }

    @Test
    void nestedMultipartIsRejected() throws Exception {
        var exc = multipartExchange(part("nested", "multipart/mixed; boundary=inner", "..."));

        assertEquals(RETURN, jpiDev.handleRequest(exc));
        assertTrue(parse(exc.getResponse()).getDetail().contains("Nested multipart"));
    }

    /** Exceeds the maxDepth of 10 configured in {@link #buildJPI(boolean)}. */
    private static String deeplyNested() {
        return repeat("[", 12) + repeat("]", 12);
    }

    private static String part(String name, String contentType, String body) {
        return "Content-Disposition: form-data; name=\"" + name + "\"\r\n"
               + (contentType == null ? "" : "Content-Type: " + contentType + "\r\n")
               + "\r\n" + body;
    }

    private static Exchange multipartExchange(String... parts) throws Exception {
        StringBuilder body = new StringBuilder();
        for (String part : parts)
            body.append("--").append(BOUNDARY).append("\r\n").append(part).append("\r\n");
        body.append("--").append(BOUNDARY).append("--\r\n");

        return Request.post("/")
                .contentType("multipart/form-data; boundary=" + BOUNDARY)
                .body(body.toString())
                .buildExchange();
    }

    private static final String BOUNDARY = "----MembraneTestBoundary";

    // --- Bounded buffering -------------------------------------------------------------------

    /** maxSize is per document, so an oversized part must be rejected without being buffered whole. */
    @Test
    void firstPartExceedingMaxSizeIsRejected() throws Exception {
        var exc = multipartExchange(
                part("first", APPLICATION_JSON, "{\"a\":\"" + repeat("x", 20000) + "\"}"),
                part("second", APPLICATION_JSON, "{\"a\":\"b\"}"));

        assertEquals(RETURN, jpiDev.handleRequest(exc));
        assertTrue(parse(exc.getResponse()).getDetail().contains("maximum size"),
                parse(exc.getResponse()).getDetail());
    }

    /**
     * The case that must not buffer: a huge non-JSON part under SKIP is discarded from the stream
     * without ever being materialised, so maxSize does not apply to it.
     */
    @Test
    void oversizedNonJsonPartIsSkippedWithoutBuffering() throws Exception {
        jpiDev.setOtherContentTypes(SKIP);
        var exc = multipartExchange(
                part("logo", "image/png", repeat("x", 50000)),
                part("data", APPLICATION_JSON, "{\"a\":\"b\"}"));

        assertEquals(CONTINUE, jpiDev.handleRequest(exc));
        assertNull(exc.getResponse());
    }

    /** Rejecting a non-JSON part needs its header only, so its body is never read. */
    @Test
    void oversizedNonJsonPartIsRejectedWithoutBuffering() throws Exception {
        var exc = multipartExchange(part("logo", "image/png", repeat("x", 50000)));

        assertEquals(RETURN, jpiDev.handleRequest(exc));
        assertTrue(parse(exc.getResponse()).getDetail().contains("is not JSON"));
    }

    private void send(String body, Outcome expectOut, Object... parameters) throws Exception {
        var exc = Request
                .post("/")
                .contentType(APPLICATION_JSON)
                .body(body)
                .buildExchange();

        if (expectOut == CONTINUE) {
            assertEquals(expectOut, jpiProd.handleRequest(exc));
            assertNull(exc.getResponse());

            assertEquals(expectOut, jpiDev.handleRequest(exc));
            assertNull(exc.getResponse());
        } else {
            assertEquals(expectOut, jpiProd.handleRequest(exc));
            assertEquals("", exc.getResponse().getBodyAsStringDecoded());

            assertEquals(expectOut, jpiDev.handleRequest(exc));

            var pd = parse(exc.getResponse());
            assertTrue(pd.getDetail().contains(parameters[2].toString()));
            assertEquals("JSON Protection Violation", pd.getTitle());
            assertEquals(parameters[0], pd.getInternal().get("line"));
            assertEquals(parameters[1], pd.getInternal().get("column"));
        }
    }
}
