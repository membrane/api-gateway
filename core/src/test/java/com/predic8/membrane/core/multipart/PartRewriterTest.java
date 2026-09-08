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

import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.http.Request;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

class PartRewriterTest {

    private static final String BOUNDARY = "----MembraneTestBoundary";

    @Test
    void rebuildingWithoutReplacementsKeepsEveryPart() throws Exception {
        Message message = multipart(
                part("data", "application/xml", "<foo/>"),
                part("logo", "image/png", "PNG"));

        List<Part> parts = MultipartUtil.split(rebuiltFrom(message, Map.of()));

        assertEquals(2, parts.size());
        assertEquals("data", parts.getFirst().getName());
        assertEquals("application/xml", parts.getFirst().getContentType());
        assertEquals("<foo/>", parts.getFirst().getBodyAsString());
        assertEquals("logo", parts.get(1).getName());
        assertEquals("PNG", parts.get(1).getBodyAsString());
    }

    @Test
    void replacingOnePartLeavesTheOthersUntouched() throws Exception {
        Message message = multipart(
                part("logo", "image/png", "PNG"),
                part("data", "application/xml", "<foo/>"),
                part("note", "text/plain", "hello"));

        List<Part> parts = MultipartUtil.split(
                rebuiltFrom(message, Map.of(1, "<bar/>".getBytes(UTF_8))));

        assertEquals(3, parts.size());
        assertEquals("PNG", parts.getFirst().getBodyAsString());
        assertEquals("<bar/>", parts.get(1).getBodyAsString());
        assertEquals("hello", parts.get(2).getBodyAsString());
    }

    @Test
    void binaryContentSurvivesTheRoundTrip() throws Exception {
        byte[] binary = new byte[256];
        for (int i = 0; i < binary.length; i++)
            binary[i] = (byte) i;
        Message message = binaryPart("blob", binary);

        List<Part> parts = MultipartUtil.split(rebuiltFrom(message, Map.of()));

        assertArrayEquals(binary, parts.getFirst().getBody());
    }

    @Test
    void headersAreCopiedThroughAsBytes() throws Exception {
        // A raw UTF-8 filename (RFC 7578) and a folded continuation line: neither survives being
        // parsed into a Header and written back out, so neither may be parsed at all.
        String header = "Content-Disposition: form-data; name=\"logo\"; filename=\"Grüße.png\"\r\n"
                        + "X-Folded: first\r\n\tsecond\r\n"
                        + "Content-Type: image/png\r\n";
        Message message = multipart(header + "\r\nPNG");

        byte[] rebuilt = PartRewriter.rebuild(message, Map.of());

        assertTrue(indexOf(rebuilt, header.getBytes(UTF_8)) >= 0,
                "header block must be reproduced byte for byte: " + new String(rebuilt, UTF_8));
    }

    @Test
    void anEmptyPartBodyIsReproduced() throws Exception {
        Message message = multipart(part("empty", "text/plain", ""));

        List<Part> parts = MultipartUtil.split(rebuiltFrom(message, Map.of()));

        assertEquals(1, parts.size());
        assertEquals("", parts.getFirst().getBodyAsString());
    }

    /**
     * A replacement carrying the message's own delimiter would split the rebuilt body where the
     * sender never put a boundary, smuggling content past whatever just inspected it.
     */
    @Test
    void aReplacementCarryingTheBoundaryIsRejected() throws Exception {
        Message message = multipart(part("data", "application/xml", "<foo/>"));
        byte[] injected = ("<foo/>\r\n--" + BOUNDARY + "--").getBytes(UTF_8);

        IOException e = assertThrows(IOException.class,
                () -> PartRewriter.rebuild(message, Map.of(0, injected)));
        assertTrue(e.getMessage().contains("MIME boundary"), e.getMessage());
    }

    /** The CRLF closing the header block supplies the delimiter's first two bytes. */
    @Test
    void aReplacementStartingWithTheBoundaryIsRejected() throws Exception {
        Message message = multipart(part("data", "application/xml", "<foo/>"));
        byte[] injected = ("--" + BOUNDARY + "\r\nContent-Type: text/plain\r\n\r\nsmuggled").getBytes(UTF_8);

        assertThrows(IOException.class, () -> PartRewriter.rebuild(message, Map.of(0, injected)));
    }

    /** Reads the rebuilt body back as a message, so it can be split again. */
    private static Message rebuiltFrom(Message message, Map<Integer, byte[]> replacements) throws Exception {
        return multipartMessage(PartRewriter.rebuild(message, replacements));
    }

    private static String part(String name, String contentType, String body) {
        return "Content-Disposition: form-data; name=\"" + name + "\"\r\n"
               + "Content-Type: " + contentType + "\r\n"
               + "\r\n" + body;
    }

    private static Message multipart(String... parts) throws Exception {
        StringBuilder body = new StringBuilder();
        for (String part : parts)
            body.append("--").append(BOUNDARY).append("\r\n").append(part).append("\r\n");
        body.append("--").append(BOUNDARY).append("--\r\n");
        return multipartMessage(body.toString().getBytes(UTF_8));
    }

    private static Message binaryPart(String name, byte[] content) throws Exception {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(("--" + BOUNDARY + "\r\nContent-Disposition: form-data; name=\"" + name + "\"\r\n"
                    + "Content-Type: application/octet-stream\r\n\r\n").getBytes(ISO_8859_1));
        body.write(content);
        body.write(("\r\n--" + BOUNDARY + "--\r\n").getBytes(ISO_8859_1));
        return multipartMessage(body.toByteArray());
    }

    private static Message multipartMessage(byte[] body) throws Exception {
        return Request.post("/")
                .contentType("multipart/form-data; boundary=" + BOUNDARY)
                .body(body)
                .buildExchange()
                .getRequest();
    }

    private static int indexOf(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++)
                if (haystack[i + j] != needle[j]) continue outer;
            return i;
        }
        return -1;
    }
}
