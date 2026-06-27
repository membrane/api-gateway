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
import com.predic8.membrane.core.http.Response;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link XOPReconstitutor}, exercising the guard conditions,
 * the base64 inlining of referenced parts and the graceful fallback behaviour.
 *
 * @see ReassembleTest for the end-to-end test against a real CXF-generated message
 */
class XOPReconstitutorTest {

    private static final String CRLF = "\r\n";
    private static final String BOUNDARY = "uuid:boundary-1234";

    private XOPReconstitutor reconstitutor;

    @BeforeEach
    void setUp() {
        reconstitutor = new XOPReconstitutor();
    }

    // -------------------------------------------------------------------------
    // Happy path
    // -------------------------------------------------------------------------

    @Test
    void inlinesReferencedPartAsBase64() throws Exception {
        Message reconstituted = reconstitutor.getReconstitutedMessage(xopResponse());

        assertNotNull(reconstituted);
        // "0123456789" base64-encoded
        assertTrue(reconstituted.getBodyAsStringDecoded().contains("MDEyMzQ1Njc4OQ=="),
                "binary part should be inlined as base64");
        // the <xop:Include> element must be gone after reconstitution
        assertFalse(reconstituted.getBodyAsStringDecoded().contains("Include"),
                "xop:Include element should have been replaced");
    }

    @Test
    void reconstitutedContentTypeComesFromInnerTypeParameter() throws Exception {
        Message reconstituted = reconstitutor.getReconstitutedMessage(xopResponse());

        assertNotNull(reconstituted);
        assertEquals("text/xml", reconstituted.getHeader().getContentType());
    }

    @Test
    void reconstituteIfNecessaryProducesReassembledStream() throws IOException {
        String actual = IOUtils.toString(reconstitutor.reconstituteIfNecessary(xopResponse()), UTF_8);

        assertFalse(actual.startsWith("--"), "stream should be reassembled, not the raw multipart body");
        assertTrue(actual.contains("MDEyMzQ1Njc4OQ=="));
    }

    // -------------------------------------------------------------------------
    // Guard conditions — getReconstitutedMessage returns null
    // -------------------------------------------------------------------------

    @Test
    void nullMessageReturnsNull() throws Exception {
        assertNull(reconstitutor.getReconstitutedMessage(null));
    }

    @Test
    void messageWithoutContentTypeReturnsNull() throws Exception {
        Response response = Response.ok().body("hello").build();
        assertNull(reconstitutor.getReconstitutedMessage(response));
    }

    @Test
    void nonMultipartMessageReturnsNull() throws Exception {
        Response response = Response.ok()
                .header("Content-Type", "text/xml")
                .body("<a/>")
                .build();
        assertNull(reconstitutor.getReconstitutedMessage(response));
    }

    @Test
    void multipartButNotXopTypeReturnsNull() throws Exception {
        Response response = response(multipartBody(),
                "multipart/related; type=\"text/xml\"; boundary=\"" + BOUNDARY
                        + "\"; start=\"<root@test>\"");
        assertNull(reconstitutor.getReconstitutedMessage(response));
    }

    @Test
    void missingStartParameterReturnsNull() throws Exception {
        Response response = response(multipartBody(),
                "multipart/related; type=\"application/xop+xml\"; boundary=\"" + BOUNDARY + "\"");
        assertNull(reconstitutor.getReconstitutedMessage(response));
    }

    @Test
    void missingBoundaryParameterReturnsNull() throws Exception {
        Response response = response(multipartBody(),
                "multipart/related; type=\"application/xop+xml\"; start=\"<root@test>\"");
        assertNull(reconstitutor.getReconstitutedMessage(response));
    }

    @Test
    void unknownStartPartReturnsNull() throws Exception {
        // start points at a Content-ID that is not present among the parts
        Response response = response(multipartBody(startPart(), binaryPart()),
                contentType("<does-not-exist@test>"));
        assertNull(reconstitutor.getReconstitutedMessage(response));
    }

    // -------------------------------------------------------------------------
    // Fallback behaviour — reconstituteIfNecessary never throws
    // -------------------------------------------------------------------------

    @Test
    void reconstituteIfNecessaryReturnsOriginalForNonXop() throws IOException {
        Response response = Response.ok()
                .header("Content-Type", "text/xml")
                .body("<a>plain</a>")
                .build();

        String actual = IOUtils.toString(reconstitutor.reconstituteIfNecessary(response), UTF_8);
        assertEquals("<a>plain</a>", actual);
    }

    @Test
    void missingReferencedPartThrowsInGetReconstitutedMessage() {
        // start part references cid:bin@test, but no such part is included
        Response response = response(multipartBody(startPart()), contentType("<root@test>"));
        assertThrows(RuntimeException.class, () -> reconstitutor.getReconstitutedMessage(response));
    }

    @Test
    void missingReferencedPartFallsBackToOriginalStream() throws IOException {
        Response response = response(multipartBody(startPart()), contentType("<root@test>"));

        // reconstituteIfNecessary swallows the failure and returns the raw body
        String actual = IOUtils.toString(reconstitutor.reconstituteIfNecessary(response), UTF_8);
        assertTrue(actual.contains("xop:Include"), "should fall back to the original multipart body");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** A complete, well-formed XOP response with one start part and one referenced binary part. */
    private static Response xopResponse() {
        return response(multipartBody(startPart(), binaryPart()), contentType("<root@test>"));
    }

    private static String contentType(String start) {
        return "multipart/related; type=\"application/xop+xml\"; boundary=\"" + BOUNDARY
                + "\"; start=\"" + start + "\"; start-info=\"text/xml\"";
    }

    private static String startPart() {
        return "Content-Type: application/xop+xml; charset=UTF-8; type=\"text/xml\";" + CRLF
                + "Content-Transfer-Encoding: binary" + CRLF
                + "Content-ID: <root@test>" + CRLF
                + CRLF
                + "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">"
                + "<soap:Body><data>"
                + "<xop:Include xmlns:xop=\"http://www.w3.org/2004/08/xop/include\" href=\"cid:bin@test\"/>"
                + "</data></soap:Body></soap:Envelope>";
    }

    private static String binaryPart() {
        return "Content-Type: application/octet-stream" + CRLF
                + "Content-Transfer-Encoding: binary" + CRLF
                + "Content-ID: <bin@test>" + CRLF
                + CRLF
                + "0123456789";
    }

    private static String multipartBody(String... parts) {
        var sb = new StringBuilder();
        for (String part : parts) {
            sb.append("--").append(BOUNDARY).append(CRLF);
            sb.append(part).append(CRLF);
        }
        sb.append("--").append(BOUNDARY).append("--").append(CRLF);
        return sb.toString();
    }

    private static Response response(String body, String contentType) {
        byte[] bytes = body.getBytes(UTF_8);
        return Response.ok()
                .header("Content-Type", contentType)
                .header("Content-Length", String.valueOf(bytes.length))
                .body(bytes)
                .build();
    }
}
