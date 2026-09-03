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

package com.predic8.membrane.core.multipart;

import com.predic8.membrane.core.http.Response;
import jakarta.mail.internet.ParseException;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.multipart.PartScanner.PartAction;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

class PartScannerTest {

    private static final String BOUNDARY = "test-boundary-123";
    private static final String CRLF = "\r\n";

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Builds a Response with the given multipart body and boundary. */
    private static Response response(String body) {
        return response(body, BOUNDARY);
    }

    private static Response response(String body, String boundary) {
        byte[] bytes = body.getBytes(UTF_8);
        return Response.ok()
                .header("Content-Type", "multipart/form-data; boundary=\"" + boundary + "\"")
                .header("Content-Length", String.valueOf(bytes.length))
                .body(bytes)
                .build();
    }

    /**
     * Builds a minimal multipart body.
     * Each {@code part} string should contain headers + blank line + body,
     * e.g. {@code "Content-Disposition: form-data; name=\"x\"\r\n\r\nvalue"}.
     */
    private static String multipartBody(String... parts) {
        var sb = new StringBuilder();
        for (String part : parts) {
            sb.append("--").append(BOUNDARY).append(CRLF);
            sb.append(part).append(CRLF);
        }
        sb.append("--").append(BOUNDARY).append("--").append(CRLF);
        return sb.toString();
    }

    private static String formField(String name, String value) {
        return "Content-Disposition: form-data; name=\"" + name + "\"" + CRLF + CRLF + value;
    }

    private static Response xopResponse() throws IOException {
        byte[] body = IOUtils.toByteArray(MultipartUtilTest.class.getResourceAsStream("/multipart/embedded-byte-array.txt"));
        return Response.ok()
                .header("Content-Type", "multipart/related; "
                        + "type=\"application/xop+xml\"; "
                        + "boundary=\"uuid:168683dc-43b3-4e71-8e66-efb633ef406b\"; "
                        + "start=\"<root.message@cxf.apache.org>\"; "
                        + "start-info=\"text/xml\"")
                .header("Content-Length", String.valueOf(body.length))
                .body(body)
                .build();
    }

    /** Records what the traversal offered and what it actually handed over. */
    private static class RecordingHandler implements PartScanner.PartHandler {
        final List<String> decided = new ArrayList<>();
        final List<Part> handled = new ArrayList<>();
        private final Function<Header, PartAction> policy;

        RecordingHandler(Function<Header, PartAction> policy) {
            this.policy = policy;
        }

        @Override
        public PartAction decide(Header partHeader) {
            decided.add(Part.nameOf(partHeader));
            return policy.apply(partHeader);
        }

        @Override
        public void handle(Part part) {
            handled.add(part);
        }
    }

    private static RecordingHandler inspectAll() {
        return new RecordingHandler(h -> PartAction.INSPECT);
    }

    @Test
    void partsAreHandedOverInOrder() throws IOException, ParseException {
        var handler = inspectAll();

        PartScanner.forEachPart(response(multipartBody(
                formField("username", "alice"),
                formField("message", "Hello World")
        )), 1024, handler);

        assertEquals(List.of("username", "message"), handler.decided);
        assertEquals("alice", handler.handled.getFirst().getBodyAsString());
        assertEquals("Hello World", handler.handled.get(1).getBodyAsString());
    }

    @Test
    void skippedPartIsNeverMaterialisedAndTraversalContinues() throws IOException, ParseException {
        var handler = new RecordingHandler(h -> "skipme".equals(Part.nameOf(h)) ? PartAction.SKIP : PartAction.INSPECT);

        PartScanner.forEachPart(response(multipartBody(
                formField("skipme", "0123456789"),
                formField("keepme", "alice")
        )), 1024, handler);

        assertEquals(List.of("skipme", "keepme"), handler.decided, "both parts must be offered");
        assertEquals(1, handler.handled.size(), "the skipped part must not be handed over");
        assertEquals("keepme", handler.handled.getFirst().getName());
    }

    @Test
    void stopEndsTraversalWithoutTouchingLaterParts() throws IOException, ParseException {
        var handler = new RecordingHandler(h -> PartAction.STOP);

        PartScanner.forEachPart(response(multipartBody(
                formField("first", "a"),
                formField("second", "b")
        )), 1024, handler);

        assertEquals(List.of("first"), handler.decided);
        assertTrue(handler.handled.isEmpty());
    }

    @Test
    void partExceedingMaxPartSizeIsRejected() {
        var handler = inspectAll();
        var msg = response(multipartBody(formField("big", "0123456789")));

        var e = assertThrows(IOException.class, () -> PartScanner.forEachPart(msg, 4, handler));

        assertTrue(e.getMessage().contains("maximum size of 4"), e.getMessage());
        assertTrue(handler.handled.isEmpty(), "an oversized part must not be handed over");
    }

    @Test
    void oversizedPartIsNotReadWhenSkipped() throws IOException, ParseException {
        // The size cap applies only to what is buffered, so a huge skipped part costs nothing.
        var handler = new RecordingHandler(h -> "huge".equals(Part.nameOf(h)) ? PartAction.SKIP : PartAction.INSPECT);

        PartScanner.forEachPart(response(multipartBody(
                formField("huge", "x".repeat(100_000)),
                formField("small", "ok")
        )), 8, handler);

        assertEquals(1, handler.handled.size());
        assertEquals("ok", handler.handled.getFirst().getBodyAsString());
    }

    /**
     * XOP is deliberately not reassembled here: reassembly buffers every attachment plus a
     * base64-inflated copy before the handler could decide anything, escaping maxPartSize.
     */
    @Test
    void xopMessageIsTraversedAsRawParts() throws IOException, ParseException {
        var handler = inspectAll();

        PartScanner.forEachPart(xopResponse(), 1024 * 1024, handler);

        assertEquals(2, handler.handled.size());
        assertEquals("application/xop+xml; charset=UTF-8; type=\"text/xml\";", handler.handled.getFirst().getContentType());
        assertEquals("application/octet-stream", handler.handled.get(1).getContentType());
        // The attachment stays a separate part rather than being inlined into the root document.
        assertTrue(handler.handled.getFirst().getBodyAsString().contains("xop:Include"));
    }

    @Test
    void oversizedXopRootIsRejectedRatherThanReassembled() {
        var e = assertThrows(IOException.class, () -> PartScanner.forEachPart(xopResponse(), 8, inspectAll()));

        assertTrue(e.getMessage().contains("maximum size of 8"), e.getMessage());
    }

    @Test
    void nestedMultipartThrows() {
        var msg = response(multipartBody(
                "Content-Disposition: form-data; name=\"nested\"" + CRLF
                + "Content-Type: multipart/mixed; boundary=inner" + CRLF + CRLF + "..."));

        assertThrows(IOException.class, () -> PartScanner.forEachPart(msg, 1024, inspectAll()));
    }
}
