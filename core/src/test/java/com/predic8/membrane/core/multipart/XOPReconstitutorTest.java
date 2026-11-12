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

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.ChunkedBody;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.http.ReadingBodyException;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.router.DummyTestRouter;
import org.apache.commons.fileupload.MultipartStream;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

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
    // Unparsable Content-Type header
    // -------------------------------------------------------------------------

    private static final String UNPARSABLE_CONTENT_TYPE = "application/json; charset";

    @Test
    void unparsableContentTypeIsRejected() {
        final var response = response("{}", UNPARSABLE_CONTENT_TYPE);

        final var e = assertThrows(com.predic8.membrane.core.http.ReadingBodyException.class,
                () -> reconstitutor.getReconstitutedMessage(response));
        assertTrue(e.belongsTo(response));
        assertInstanceOf(com.predic8.membrane.core.http.InvalidContentTypeException.class, e.getCause());
        assertEquals("Content-Type header could not be parsed: In parameter list <; charset>, expected '=', got \"null\"",
                e.getCause().getMessage());
    }

    @Test
    void unparsableContentTypeFallsBackToOriginalStream() throws IOException {
        final var response = response("{}", UNPARSABLE_CONTENT_TYPE);

        assertEquals("{}", IOUtils.toString(reconstitutor.reconstituteIfNecessary(response), UTF_8));
    }

    /**
     * Decoding the body of a message with an unparsable Content-Type is the sender's error for a
     * request (400) and the gateway's for a backend response (500), not an internal error for both.
     */
    @Test
    void unparsableContentTypeIsAttributedToTheSourceMessage() throws Exception {
        final var router = new DummyTestRouter();
        for (final var requestBody : new boolean[]{true, false}) {
            final var exc = Request.post("/json").buildExchange();
            final Message message;
            if (requestBody) {
                exc.getRequest().setBodyContent("{}".getBytes(UTF_8));
                exc.getRequest().getHeader().setContentType(UNPARSABLE_CONTENT_TYPE);
                message = exc.getRequest();
            } else {
                exc.setResponse(response("{}", UNPARSABLE_CONTENT_TYPE));
                message = exc.getResponse();
            }
            final var decoder = new AbstractInterceptor() {
                @Override
                public Outcome handleRequest(Exchange exchange) {
                    try (final var input = message.getBodyAsStreamDecoded()) {
                        input.readAllBytes();
                    } catch (IOException e) {
                        throw new java.io.UncheckedIOException(e);
                    }
                    return Outcome.CONTINUE;
                }
            };
            decoder.init(router);
            assertEquals(Outcome.ABORT, router.getFlowController().invokeRequestHandlers(exc, List.of(decoder)));
            assertEquals(requestBody ? 400 : 500, exc.getResponse().getStatusCode());
        }
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
    void missingReferencedPartIsRejected() {
        // start part references cid:bin@test, but no such part is included
        final var response = response(multipartBody(startPart()), contentType("<root@test>"));

        final var e = assertThrows(com.predic8.membrane.core.http.ReadingBodyException.class,
                () -> reconstitutor.getReconstitutedMessage(response));
        assertTrue(e.belongsTo(response));
        assertEquals("Did not find multipart with id bin@test.", e.getCause().getMessage());
    }

    @Test
    void notWellFormedStartPartIsRejected() {
        final var startPart = startPart().replace("</soap:Envelope>", "");
        final var response = response(multipartBody(startPart, binaryPart()), contentType("<root@test>"));

        final var e = assertThrows(com.predic8.membrane.core.http.ReadingBodyException.class,
                () -> reconstitutor.getReconstitutedMessage(response));
        assertTrue(e.belongsTo(response));
        assertEquals("Start part of XOP package is not well-formed XML.", e.getCause().getMessage());
    }

    @Test
    void notWellFormedStartPartFallsBackToOriginalStream() throws IOException {
        final var startPart = startPart().replace("</soap:Envelope>", "");
        final var response = response(multipartBody(startPart, binaryPart()), contentType("<root@test>"));

        final var actual = IOUtils.toString(reconstitutor.reconstituteIfNecessary(response), UTF_8);
        assertTrue(actual.contains("xop:Include"), "should fall back to the original multipart body");
    }

    @Test
    void missingReferencedPartFallsBackToOriginalStream() throws IOException {
        Response response = response(multipartBody(startPart()), contentType("<root@test>"));

        // reconstituteIfNecessary swallows the failure and returns the raw body
        String actual = IOUtils.toString(reconstitutor.reconstituteIfNecessary(response), UTF_8);
        assertTrue(actual.contains("xop:Include"), "should fall back to the original multipart body");
    }

    @Test
    void entityDeclaredInStartPartIsNotExpanded() throws IOException {
        final var startPart = startPart().replace("<soap:Envelope ",
                "<!DOCTYPE soap:Envelope [<!ENTITY e \"xyz\">]><soap:Envelope a=\"&e;&e;\" ");
        final var response = response(multipartBody(startPart, binaryPart()), contentType("<root@test>"));

        assertFalse(IOUtils.toString(reconstitutor.reconstituteIfNecessary(response), UTF_8).contains("xyzxyz"));
    }

    @Test
    void inlinesDistinctReferencedParts() throws Exception {
        Response response = response(
                multipartBody(startPart("bin@test", "other@test"), binaryPart(), binaryPart("other@test", "abc")),
                contentType("<root@test>"));

        String reconstituted = reconstitutor.getReconstitutedMessage(response).getBodyAsStringDecoded();

        assertTrue(reconstituted.contains("MDEyMzQ1Njc4OQ=="));
        assertTrue(reconstituted.contains("YWJj"));
    }

    /**
     * MEM-38 F-052: every reference used to inline the part again, so a small package referencing one
     * attachment many times expanded without bound. SOAP 1.2 MTOM (4.3.1.1) forbids referencing an
     * extracted part from more than one xop:Include, and XOP 1.0 serializes each optimized element
     * into a new part.
     */
    @Test
    void partReferencedTwiceIsRejected() {
        Response response = response(multipartBody(startPart("bin@test", "bin@test"), binaryPart()),
                contentType("<root@test>"));

        final var e = assertThrows(com.predic8.membrane.core.http.ReadingBodyException.class,
                () -> reconstitutor.getReconstitutedMessage(response));
        assertTrue(e.belongsTo(response));
        assertEquals("Multipart with id bin@test is referenced more than once.", e.getCause().getMessage());
    }

    @Test
    void partReferencedTwiceFallsBackToOriginalStream() throws IOException {
        final var response = response(multipartBody(startPart("bin@test", "bin@test"), binaryPart()),
                contentType("<root@test>"));

        final var actual = IOUtils.toString(reconstitutor.reconstituteIfNecessary(response), UTF_8);
        assertTrue(actual.contains("xop:Include"), "should fall back to the original multipart body");
        assertFalse(actual.contains("MDEyMzQ1Njc4OQ=="), "referenced part must not be inlined");
    }

    @Test
    void truncatedPackageIsRejected() {
        final var body = multipartBody(startPart(), binaryPart());
        final var response = response(body.substring(0, body.indexOf("0123456789")), contentType("<root@test>"));

        final var e = assertThrows(ReadingBodyException.class, () -> reconstitutor.getReconstitutedMessage(response));
        assertTrue(e.belongsTo(response));
        assertInstanceOf(XopReassemblyException.class, e.getCause());
        assertInstanceOf(MultipartStream.MalformedStreamException.class, e.getCause().getCause());
    }

    @Test
    void unsupportedContentTransferEncodingIsRejected() {
        final var response = response(
                multipartBody(startPart(), binaryPart().replace("Transfer-Encoding: binary", "Transfer-Encoding: base64")),
                contentType("<root@test>"));

        final var e = assertThrows(ReadingBodyException.class, () -> reconstitutor.getReconstitutedMessage(response));
        assertTrue(e.belongsTo(response));
        assertInstanceOf(XopReassemblyException.class, e.getCause());
        assertEquals("XOP package is malformed: Content-Transfer-Encoding 'base64' is not supported.", e.getCause().getMessage());
    }

    @Test
    void partHeaderLineWithoutColonIsRejected() {
        final var response = response(multipartBody(startPart(), binaryPartWithHeaderLineWithoutColon()),
                contentType("<root@test>"));

        final var e = assertThrows(ReadingBodyException.class, () -> reconstitutor.getReconstitutedMessage(response));
        assertTrue(e.belongsTo(response));
        assertInstanceOf(XopReassemblyException.class, e.getCause());
        assertEquals("XOP package is malformed: Malformed multipart part header: a line has no colon.", e.getCause().getMessage());
    }

    /**
     * A transport failure of a chunked body also reaches the multipart split as an IOException. It
     * is not a broken package, so it keeps the plain wrapping.
     */
    @Test
    void chunkedTransportFailureIsNotAnXopFailure() {
        final var body = multipartBody(startPart(), binaryPart());
        final var truncated = body.substring(0, body.indexOf("0123456789")).getBytes(UTF_8);
        final var chunked = new ByteArrayOutputStream();
        chunked.writeBytes("%x\r\n".formatted(truncated.length).getBytes(UTF_8));
        chunked.writeBytes(truncated);
        chunked.writeBytes("\r\n".getBytes(UTF_8)); // the connection ends where the next chunk-size is due
        final var response = Response.ok().header("Content-Type", contentType("<root@test>")).build();
        response.getHeader().add("Transfer-Encoding", "chunked");
        response.setBody(new ChunkedBody(new ByteArrayInputStream(chunked.toByteArray())));

        final var e = assertThrows(ReadingBodyException.class, () -> reconstitutor.getReconstitutedMessage(response));
        assertTrue(e.belongsTo(response));
        assertTrue(response.getBody().hasFailed());
        assertNull(ExceptionUtils.throwableOfType(e, XopReassemblyException.class));
    }

    @Test
    void includeWithoutHrefIsRejected() {
        final var response = response(multipartBody(startPart().replace(" href=\"cid:bin@test\"", ""), binaryPart()),
                contentType("<root@test>"));

        final var e = assertThrows(ReadingBodyException.class, () -> reconstitutor.getReconstitutedMessage(response));
        assertTrue(e.belongsTo(response));
        assertInstanceOf(XopReassemblyException.class, e.getCause());
        assertEquals("xop:Include without href attribute.", e.getCause().getMessage());
    }

    @Test
    void startPartWithoutContentTypeReturnsNull() throws Exception {
        final var response = response(
                multipartBody(startPart().replaceFirst("Content-Type: [^\r]*\r\n", ""), binaryPart()),
                contentType("<root@test>"));

        assertNull(reconstitutor.getReconstitutedMessage(response));
    }

    @Test
    void startPartWithUnparsableContentTypeReturnsNull() throws Exception {
        final var response = response(
                multipartBody(startPart().replaceFirst("Content-Type: [^\r]*", "Content-Type: application/xop+xml; type"), binaryPart()),
                contentType("<root@test>"));

        assertNull(reconstitutor.getReconstitutedMessage(response));
    }

    @Test
    void duplicateReferencesAreAttributedToTheSourceMessage() throws Exception {
        final var body = multipartBody(startPart("bin@test", "bin@test"), binaryPart());
        assertEquals(400, statusAfterDecoding(body, true));
        assertEquals(500, statusAfterDecoding(body, false));
    }

    /**
     * Issue #3692: a package that cannot be reassembled is the sender's fault. A request is answered
     * with 400, not with the 500 of an internal error.
     */
    @Test
    void malformedRequestPackagesAreAnsweredWith400() throws Exception {
        final var complete = multipartBody(startPart(), binaryPart());
        for (final var body : List.of(
                multipartBody(startPart()), // referenced part is missing
                complete.substring(0, complete.indexOf("0123456789")), // truncated
                multipartBody(startPart(), binaryPart().replace("Transfer-Encoding: binary", "Transfer-Encoding: base64")),
                multipartBody(startPart().replace(" href=\"cid:bin@test\"", ""), binaryPart()),
                multipartBody(startPart(), binaryPartWithHeaderLineWithoutColon()))) {
            assertEquals(400, statusAfterDecoding(body, true), body);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Lets an interceptor decode the XOP package in the request flow, from the request or from the
     * response, and returns the status code of the resulting error response.
     */
    private static int statusAfterDecoding(String body, boolean requestBody) throws Exception {
        final var router = new DummyTestRouter();
        final var exc = Request.post("/soap").buildExchange();
        final Message message;
        if (requestBody) {
            exc.getRequest().setBodyContent(body.getBytes(UTF_8));
            exc.getRequest().getHeader().setContentType(contentType("<root@test>"));
            message = exc.getRequest();
        } else {
            exc.setResponse(response(body, contentType("<root@test>")));
            message = exc.getResponse();
        }
        final var decoder = new AbstractInterceptor() {
            @Override
            public Outcome handleRequest(Exchange exchange) {
                try (final var input = message.getBodyAsStreamDecoded()) {
                    input.readAllBytes();
                } catch (IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
                return Outcome.CONTINUE;
            }
        };
        decoder.init(router);
        assertEquals(Outcome.ABORT, router.getFlowController().invokeRequestHandlers(exc, List.of(decoder)));
        return exc.getResponse().getStatusCode();
    }

    /** A complete, well-formed XOP response with one start part and one referenced binary part. */
    private static Response xopResponse() {
        return response(multipartBody(startPart(), binaryPart()), contentType("<root@test>"));
    }

    private static String contentType(String start) {
        return "multipart/related; type=\"application/xop+xml\"; boundary=\"" + BOUNDARY
                + "\"; start=\"" + start + "\"; start-info=\"text/xml\"";
    }

    private static String startPart() {
        return startPart("bin@test");
    }

    private static String startPart(String... referencedIds) {
        var includes = new StringBuilder();
        for (String id : referencedIds)
            includes.append("<xop:Include xmlns:xop=\"http://www.w3.org/2004/08/xop/include\" href=\"cid:%s\"/>".formatted(id));
        return "Content-Type: application/xop+xml; charset=UTF-8; type=\"text/xml\";" + CRLF
                + "Content-Transfer-Encoding: binary" + CRLF
                + "Content-ID: <root@test>" + CRLF
                + CRLF
                + "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">"
                + "<soap:Body><data>"
                + includes
                + "</data></soap:Body></soap:Envelope>";
    }

    private static String binaryPart() {
        return binaryPart("bin@test", "0123456789");
    }

    /** The broken line is the Content-Type, so the part keeps its Content-ID and is still found. */
    private static String binaryPartWithHeaderLineWithoutColon() {
        return binaryPart().replace("Content-Type: application/octet-stream", "Content-Type application/octet-stream");
    }

    private static String binaryPart(String id, String content) {
        return "Content-Type: application/octet-stream" + CRLF
                + "Content-Transfer-Encoding: binary" + CRLF
                + "Content-ID: <" + id + ">" + CRLF
                + CRLF
                + content;
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
