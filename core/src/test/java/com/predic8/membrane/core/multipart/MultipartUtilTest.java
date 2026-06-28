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

import java.io.IOException;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

class MultipartUtilTest {

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

    // -------------------------------------------------------------------------
    // split(Message) — auto-reads boundary from Content-Type
    // -------------------------------------------------------------------------

    @Test
    void twoFormFieldsAreReturnedInOrder() throws IOException, ParseException {
        var parts = MultipartUtil.split(response(multipartBody(
                formField("username", "alice"),
                formField("message", "Hello World")
        )));

        assertEquals(2, parts.size());
        assertEquals("username", parts.get(0).getName());
        assertEquals("alice",    parts.get(0).getBodyAsString());
        assertEquals("message",  parts.get(1).getName());
        assertEquals("Hello World", parts.get(1).getBodyAsString());
    }

    @Test
    void fileUploadPartExposesFilenameAndContentType() throws IOException, ParseException {
        String part = "Content-Disposition: form-data; name=\"upload\"; filename=\"photo.jpg\"" + CRLF
                    + "Content-Type: image/jpeg" + CRLF
                    + CRLF
                    + "JFIF";

        var parts = MultipartUtil.split(response(multipartBody(part)));

        assertEquals(1, parts.size());
        assertEquals("upload",     parts.get(0).getName());
        assertEquals("photo.jpg",  parts.get(0).getFilename());
        assertEquals("image/jpeg", parts.get(0).getContentType());
        assertArrayEquals("JFIF".getBytes(UTF_8), parts.get(0).getBody());
    }

    @Test
    void partWithContentIdIsAccessible() throws IOException, ParseException {
        String part = "Content-Type: application/octet-stream" + CRLF
                    + "Content-ID: <abc-123@example.com>" + CRLF
                    + CRLF
                    + "binary";

        var parts = MultipartUtil.split(response(multipartBody(part)));

        assertEquals("<abc-123@example.com>", parts.get(0).getContentID());
    }

    @Test
    void binaryBodyIsPreservedExactly() throws IOException, ParseException {
        byte[] payload = {0, 1, 2, (byte) 0xFF, (byte) 0xFE};
        String header = "Content-Type: application/octet-stream" + CRLF + CRLF;
        byte[] partBytes = (header).getBytes(UTF_8);
        byte[] fullPart = new byte[partBytes.length + payload.length];
        System.arraycopy(partBytes, 0, fullPart, 0, partBytes.length);
        System.arraycopy(payload, 0, fullPart, partBytes.length, payload.length);

        // Build body manually to embed raw bytes
        byte[] prefix = ("--" + BOUNDARY + CRLF).getBytes(UTF_8);
        byte[] suffix = (CRLF + "--" + BOUNDARY + "--" + CRLF).getBytes(UTF_8);
        byte[] body = new byte[prefix.length + fullPart.length + suffix.length];
        System.arraycopy(prefix, 0, body, 0, prefix.length);
        System.arraycopy(fullPart, 0, body, prefix.length, fullPart.length);
        System.arraycopy(suffix, 0, body, prefix.length + fullPart.length, suffix.length);

        byte[] msgBytes = body;
        var msg = Response.ok()
                .header("Content-Type", "multipart/form-data; boundary=\"" + BOUNDARY + "\"")
                .header("Content-Length", String.valueOf(msgBytes.length))
                .body(msgBytes)
                .build();

        var parts = MultipartUtil.split(msg);
        assertArrayEquals(payload, parts.get(0).getBody());
    }

    // -------------------------------------------------------------------------
    // split(Message, boundary) — explicit boundary overload
    // -------------------------------------------------------------------------

    @Test
    void explicitBoundaryOverloadProducesSameResult() throws IOException {
        var body = multipartBody(formField("x", "42"));
        byte[] bytes = body.getBytes(UTF_8);
        var msg = Response.ok()
                .header("Content-Type", "multipart/form-data; boundary=\"other\"") // intentionally wrong
                .header("Content-Length", String.valueOf(bytes.length))
                .body(bytes)
                .build();

        // Pass the correct boundary explicitly — Content-Type boundary is ignored
        var parts = MultipartUtil.split(msg, BOUNDARY);

        assertEquals(1, parts.size());
        assertEquals("x",  parts.get(0).getName());
        assertEquals("42", parts.get(0).getBodyAsString());
    }

    // -------------------------------------------------------------------------
    // Real-world resource: XOP multipart from ReassembleTest
    // -------------------------------------------------------------------------

    @Test
    void xopResourceSplitsIntoTwoParts() throws IOException {
        byte[] body = IOUtils.toByteArray(getClass().getResourceAsStream("/multipart/embedded-byte-array.txt"));
        var response = Response.ok()
                .header("Content-Type", "multipart/related; "
                        + "type=\"application/xop+xml\"; "
                        + "boundary=\"uuid:168683dc-43b3-4e71-8e66-efb633ef406b\"; "
                        + "start=\"<root.message@cxf.apache.org>\"; "
                        + "start-info=\"text/xml\"")
                .header("Content-Length", String.valueOf(body.length))
                .body(body)
                .build();

        var parts = MultipartUtil.split(response, "uuid:168683dc-43b3-4e71-8e66-efb633ef406b");

        assertEquals(2, parts.size());
        assertEquals("<root.message@cxf.apache.org>",                parts.get(0).getContentID());
        assertEquals("<a416c16d-a50e-44ca-a6d7-3e8a61480afa-2@cxf.apache.org>", parts.get(1).getContentID());
        assertEquals("application/xop+xml; charset=UTF-8; type=\"text/xml\";", parts.get(0).getContentType());
        assertEquals("application/octet-stream",                      parts.get(1).getContentType());
    }

    // -------------------------------------------------------------------------
    // Error cases
    // -------------------------------------------------------------------------

    @Test
    void missingContentTypeThrows() {
        byte[] bytes = "body".getBytes(UTF_8);
        var msg = Response.ok()
                .header("Content-Length", String.valueOf(bytes.length))
                .body(bytes)
                .build();

        assertThrows(IOException.class, () -> MultipartUtil.split(msg));
    }

    @Test
    void missingBoundaryParameterThrows() {
        byte[] bytes = "body".getBytes(UTF_8);
        var msg = Response.ok()
                .header("Content-Type", "multipart/form-data")   // no boundary=
                .header("Content-Length", String.valueOf(bytes.length))
                .body(bytes)
                .build();

        assertThrows(IOException.class, () -> MultipartUtil.split(msg));
    }

    @Test
    void unsupportedContentTransferEncodingThrows() {
        String part = "Content-Disposition: form-data; name=\"x\"" + CRLF
                    + "Content-Transfer-Encoding: quoted-printable" + CRLF
                    + CRLF
                    + "value";

        assertThrows(IOException.class,
                () -> MultipartUtil.split(response(multipartBody(part))));
    }
}
