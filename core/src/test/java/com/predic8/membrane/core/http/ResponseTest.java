/* Copyright 2009, 2011 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */


package com.predic8.membrane.core.http;

import com.predic8.membrane.core.util.EndOfStreamException;
import com.predic8.membrane.core.util.StringTestUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.stream.Stream;

import static com.predic8.membrane.core.http.MimeType.TEXT_HTML;
import static com.predic8.membrane.core.http.MimeType.isOfMediaType;
import static com.predic8.membrane.core.http.Response.*;
import static com.predic8.membrane.core.util.HttpTestUtil.convertMessage;
import static com.predic8.membrane.test.TestUtil.getResourceAsStream;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.of;

public class ResponseTest {

    private Response res1;
    private Response res2;
    private Response res3;

    private InputStream in1;
    private InputStream in2;
    private InputStream in3;

    private ByteArrayOutputStream tempOut;

    private InputStream tempIn;

    @BeforeEach
    public void setUp() {
        in1 = getResourceAsStream(this,"response-unchunked-html.msg");
        in2 = getResourceAsStream(this,"response-unchunked-image.msg");
        in3 = getResourceAsStream(this,"response-chunked-html.msg");

        res1 = new Response();
        res2 = new Response();
        res3 = new Response();
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (in1 != null) {
            in1.close();
        }

        if (in2 != null) {
            in2.close();
        }

        if (in3 != null) {
            in3.close();
        }

        if (tempOut != null)
            tempOut.close();

        if (tempIn != null)
            tempIn.close();
    }

    @Test
    void parseStartLine1() throws IOException {
        res1.parseStartLine(in1);
        assertEquals(200, res1.getStatusCode());
        assertEquals("1.1", res1.getVersion());
    }

    @Test
    void testParseStartLine2() throws IOException {
        res2.parseStartLine(in2);
        assertEquals(200, res2.getStatusCode());
        assertEquals("1.1", res2.getVersion());
    }

    @Test
    void testParseStartLine3() throws IOException {
        res3.parseStartLine(in3);
        assertEquals(200, res3.getStatusCode());
        assertEquals("1.1", res3.getVersion());
    }

    @Test
    void testUnchunkedHtmlRead() throws Exception {
        res1.read(in1, true);
        assertEquals(200, res1.getStatusCode());
        assertTrue(res1.isHTTP11());
        assertTrue(res1.isKeepAlive());
        assertNotNull(res1.getBody());
        assertEquals(6122, res1.getBody().getLength());
        assertEquals(6122, res1.getHeader().getContentLength());
    }

    @Test
    void testUnchunkedHtmlWrite() throws Exception {
        tempOut = new ByteArrayOutputStream();
        res1.read(in1, true);
        res1.write(tempOut, true);

        tempIn = new ByteArrayInputStream(tempOut.toByteArray());

        Response resTemp = new Response();
        resTemp.read(tempIn, true);

        assertEquals(res1.getStatusCode(), resTemp.getStatusCode());
        assertEquals(res1.getStatusMessage(), resTemp.getStatusMessage());
        assertArrayEquals(res1.getBody().getContent(), resTemp.getBody().getContent());
        assertArrayEquals(res1.getBody().getRaw(), resTemp.getBody().getRaw());
    }

    @Test
    void testUnchunkedImageRead() throws Exception {
        res2.read(in2, true);
        assertEquals(200, res2.getStatusCode());
        assertTrue(res2.isHTTP11());
        assertTrue(res2.isKeepAlive());
        assertNotNull(res2.getBody());
        assertEquals(21621, res2.getBody().getLength());
        assertEquals(21621, res2.getHeader().getContentLength());
    }


    @Test
    void testUnchunkedImageWrite() throws Exception {
        tempOut = new ByteArrayOutputStream();
        res2.read(in2, true);
        res2.write(tempOut, true);


        tempIn = new ByteArrayInputStream(tempOut.toByteArray());

        Response resTemp = new Response();
        resTemp.read(tempIn, true);

        assertEquals(res2.getStatusCode(), resTemp.getStatusCode());
        assertEquals(res2.getStatusMessage(), resTemp.getStatusMessage());

        assertEquals(res2.getBody().getContent().length, resTemp.getBody().getContent().length);
        assertArrayEquals(res2.getBody().getContent(), resTemp.getBody().getContent());
    }

    @Test
    void testChunkedHtmlRead() throws Exception {
        res3.read(in3, true);
        assertEquals(200, res3.getStatusCode());
        assertTrue(res3.isHTTP11());
        assertTrue(res3.isKeepAlive());
        assertNotNull(res3.getBody());
    }


    @Test
    void testChunkedHtmlWrite() throws Exception {
        tempOut = new ByteArrayOutputStream();
        res3.read(in3, true);
        res3.write(tempOut, true);

        tempIn = new ByteArrayInputStream(tempOut.toByteArray());

        Response resTemp = new Response();
        resTemp.read(tempIn, true);

        assertEquals(res3.getStatusCode(), resTemp.getStatusCode());
        assertEquals(res3.getStatusMessage(), resTemp.getStatusMessage());

        if (!res3.getBody().wasStreamed()) {
            assertEquals(res3.getBody().getContent().length, resTemp.getBody().getContent().length);
            assertArrayEquals(res3.getBody().getContent(), resTemp.getBody().getContent());
        } else
            assertEquals(0, res3.getBody().getContent().length);
    }

    @Test
    void testWithNoContentLength() throws Exception {
        res3.read(getResourceAsStream(this,"response-no-content-length.txt"), true);
        assertEquals(185, res3.getBody().getLength());
    }

    @Test
    void isEmpty() {
        assertTrue(ok().build().isBodyEmpty());
    }

    @Test
    void isNotEmpty() {
        assertFalse(ok("ABC").build().isBodyEmpty());
    }

    @ParameterizedTest
    @MethodSource("statusCodeMessageGenerator")
    void statusCodeBuilders(ResponseBuilder builder, int statusCode, String msg, boolean checkBody) {
        Response r = builder.build();
        assertEquals(statusCode, r.getStatusCode());
        assertEquals(msg, r.getStatusMessage());

        if (!checkBody)
            return;

        assertTrue(r.getBodyAsStringDecoded().contains("ABC"));
    }

    private static Stream<Arguments> statusCodeMessageGenerator() {
        return Stream.of(
            // Do not check body
            of(ok(), 200, "OK", false),
            of(noContent(), 204, "No Content", false),
            of(forbidden(), 403, "Forbidden", false),
            of(continue100(), 100, "Continue", false),
            of(badRequest(), 400, "Bad Request", false),
            of(methodNotAllowed(), 405, "Method Not Allowed", false),
            of(unauthorized(),401 , "Unauthorized", false),
            of(Response.notFound(), 404 , "Not Found", false),

            // Check body
            of(notModified("ABC"), 304 , "Not Modified", false),
            of(serviceUnavailable("ABC"), 503 , "Service Unavailable", true),
            of(badGateway("ABC"), 502, "Bad Gateway", true),
            of(unauthorized("ABC"), 401 , "Unauthorized", true),
            of(gatewayTimeout("ABC"), 504, "Gateway Timeout", true),
            of(redirect("ABC", 307), 307, "Temporary Redirect", true),
            of(redirect("ABC", 301), 301, "Moved Permanently", true),
            of(statusCode(999), 999, "Unknown", false)
        );
    }

    @Test
    void redirectTest() {
        assertTrue(Response.redirect("https://predic8.de/foo", false).build().getBodyAsStringDecoded().contains("""
                <a href="https://predic8.de/foo">https://predic8.de/foo</a>"""));
    }

    @Test
    void fromStatusCodeTest() {
        Response response = fromStatusCode(200,"The Message <b>is</b> this!").build();
        assertEquals(200,response.getStatusCode());
        assertTrue(isOfMediaType( TEXT_HTML,response.getHeader().getContentType()));
        assertEquals("""
                <html><head><title>200 OK.</title></head><body><h1>200 OK.</h1><p>The Message <b>is</b> this!</p></body></html>""",
                response.getBodyAsStringDecoded());
    }

    @Test
    void redirectWithout300Test() {
        Response res = Response.redirectWithout300("http://localhost:2000/login","New <b>address</b>!").build();
        assertEquals(200,res.getStatusCode());
        assertTrue(isOfMediaType( TEXT_HTML,res.getHeader().getContentType()));
        assertEquals("http://localhost:2000/login",res.getHeader().getLocation());
        assertTrue( res.getBodyAsStringDecoded().contains("""
            <meta http-equiv="refresh" content="0;URL='http://localhost:2000/login'"/>"""));
    }

    @Test
    void readResponseWithBodyContent() throws IOException, EndOfStreamException {
        Response res = Response.noContent().build();
        res.read(getResourceAsStream(this,"response-no-content-length.txt"),true);
        assertFalse(res.isBodyEmpty());
        assertInstanceOf(Body.class, res.getBody());
    }

    @Test
    void readResponseWithBodyContentChunked() throws IOException, EndOfStreamException {
        Response res = Response.noContent().build();
        res.read(getResourceAsStream(this,"response-chunked.txt"),true);
        assertFalse(res.isBodyEmpty());
        assertInstanceOf(ChunkedBody.class, res.getBody());
    }

    @Test
    void readResponseNoBodyContent204() throws IOException, EndOfStreamException {
        Response res = new Response();
        res.read(getResourceAsStream(this,"response-no-content.http"),true);
        assertTrue(res.isBodyEmpty());
        assertInstanceOf(EmptyBody.class, res.getBody());
    }

    @Test
    void readResponseNoBodyContent205() throws IOException, EndOfStreamException {
        Response res = new Response();
        res.read(getResourceAsStream(this,"response-205-reset.http"),true);
        assertTrue(res.isBodyEmpty());
        assertInstanceOf(EmptyBody.class, res.getBody());
    }

    /**
     * RFC 9112 5.1 forbids whitespace between a field name and the colon in either direction. A
     * response carrying it desyncs the connection the same way a request does: the name keeps the
     * whitespace and stops matching Content-Length, so Membrane reads no body while the declared
     * bytes stay in the stream and are parsed as the next response.
     */
    @ParameterizedTest
    @ValueSource(strings = {"Content-Length : 6", "Content-Length\t: 6"})
    void headerLineWithWhitespaceBeforeColonIsRejected(String fieldLine) {
        assertThrows(MalformedHeaderException.class, () -> readResponse("""
                HTTP/1.1 200 Ok
                Content-Type: text/plain
                %s

                """.formatted(fieldLine)));
    }

    @Test
    void headerLineWithWhitespaceAfterColonIsAccepted() throws Exception {
        assertEquals("text/plain", readResponse("""
                HTTP/1.1 200 Ok
                Content-Type:\ttext/plain
                Content-Length: 0

                """).getHeader().getContentType());
    }

    /**
     * The two field lines combine to "gzip, chunked", whose final coding is "chunked", so the
     * response is chunked-framed. Reading only the first field line makes Membrane frame it as a
     * plain body and hand the chunk sizes through as content (#3327).
     */
    @Test
    void transferEncodingEndingInChunkedAcrossSeveralFieldsIsChunkedFramed() throws Exception {
        assertInstanceOf(ChunkedBody.class, readResponse("""
                HTTP/1.1 200 Ok
                Transfer-Encoding: gzip
                Transfer-Encoding: chunked

                0

                """).getBody());
    }

    /**
     * A present but empty Transfer-Encoding carries no coding and therefore does not end in
     * "chunked", so the body length of the response cannot be determined and it is rejected
     * instead of being framed by its Content-Length.
     */
    @Test
    void emptyTransferEncodingIsRejected() {
        assertThrows(MalformedHeaderException.class, () -> readResponse("""
                HTTP/1.1 200 Ok
                Transfer-Encoding:
                Content-Length: 3

                abc
                """));
    }

    /**
     * RFC 9112 6.3: a response with both Transfer-Encoding and Content-Length might indicate an
     * attempt at response splitting and ought to be handled as an error, so it is rejected.
     */
    @Test
    void chunkedWithContentLengthIsRejected() {
        assertThrows(MalformedHeaderException.class, () -> readResponse("""
                HTTP/1.1 200 Ok
                Transfer-Encoding: chunked
                Content-Length: 0

                5
                abcde
                0

                """));
    }

    @Test
    void chunkedAcrossSeveralFieldsWithContentLengthIsRejected() {
        assertThrows(MalformedHeaderException.class, () -> readResponse("""
                HTTP/1.1 302 Found
                Location: https://example.com/
                Transfer-Encoding: gzip
                Transfer-Encoding: chunked
                Content-Length: 3

                0

                """));
    }

    /**
     * A response that must not contain a body carries no framing to validate, so framing fields
     * it happens to carry are not rejected - and the next response on the connection is read intact.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "204 No Content\nTransfer-Encoding: chunked\nContent-Length: 5",
            "204 No Content\nTransfer-Encoding: gzip",
    })
    void responseWithoutBodyIgnoresFraming(String statusAndFraming) throws Exception {
        InputStream in = convertMessage("""
                HTTP/1.1 %s

                HTTP/1.1 200 Ok
                Content-Length: 2

                ok""".formatted(statusAndFraming));

        Response first = new Response();
        first.read(in, true);
        assertInstanceOf(EmptyBody.class, first.getBody());

        Response next = new Response();
        next.read(in, true);
        assertEquals(200, next.getStatusCode());
        assertEquals("ok", next.getBodyAsStringDecoded());
    }

    private static Response readResponse(String message) throws IOException, EndOfStreamException {
        Response res = new Response();
        res.read(convertMessage(message), true);
        return res;
    }

    @Nested
    class RealResponses {

        private static final String chunkedCharset = """
            HTTP/1.1 404 Not Found
            date: Fri, 09 Jan 2026 13:29:16 GMT
            content-type: text/plain; charset=us-ascii
            transfer-encoding: chunked
            set-cookie: cc370ea6bd994adee940ea801664=09ea03894683df52e5cfd3fa1c8; path=/; HttpOnly; Secure; SameSite=None
            Strict-Transport-Security: max-age=157680000; includeSubDomains
            
            17
            No Mapping Rule matched
            0
            
            """;

        @Test
        void chunkedCharset() throws Exception {
            Response res = new Response();
            res.read(new ByteArrayInputStream( StringTestUtil.normalizeCRLF(chunkedCharset).getBytes()),true);
            assertEquals(404, res.getStatusCode());
            assertEquals("text/plain; charset=us-ascii", res.getHeader().getContentType());
            assertEquals("No Mapping Rule matched",res.getBodyAsStringDecoded());
        }
    }
}
