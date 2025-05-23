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

import com.predic8.membrane.core.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;

import java.io.*;
import java.util.stream.*;

import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.http.Response.*;
import static com.predic8.membrane.test.TestUtil.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.*;

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
    public void testParseStartLine1() throws IOException, EndOfStreamException {
        res1.parseStartLine(in1);
        assertEquals(200, res1.getStatusCode());
        assertEquals("1.1", res1.getVersion());
    }

    @Test
    public void testParseStartLine2() throws IOException, EndOfStreamException {
        res2.parseStartLine(in2);
        assertEquals(200, res2.getStatusCode());
        assertEquals("1.1", res2.getVersion());
    }

    @Test
    public void testParseStartLine3() throws IOException, EndOfStreamException {
        res3.parseStartLine(in3);
        assertEquals(200, res3.getStatusCode());
        assertEquals("1.1", res3.getVersion());
    }

    @Test
    public void testUnchunkedHtmlRead() throws Exception {
        res1.read(in1, true);
        assertEquals(200, res1.getStatusCode());
        assertTrue(res1.isHTTP11());
        assertTrue(res1.isKeepAlive());
        assertNotNull(res1.getBody());
        assertEquals(6122, res1.getBody().getLength());
        assertEquals(6122, res1.getHeader().getContentLength());
    }

    @Test
    public void testUnchunkedHtmlWrite() throws Exception {
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
    public void testUnchunkedImageRead() throws Exception {
        res2.read(in2, true);
        assertEquals(200, res2.getStatusCode());
        assertTrue(res2.isHTTP11());
        assertTrue(res2.isKeepAlive());
        assertNotNull(res2.getBody());
        assertEquals(21621, res2.getBody().getLength());
        assertEquals(21621, res2.getHeader().getContentLength());
    }


    @Test
    public void testUnchunkedImageWrite() throws Exception {
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
    public void testChunkedHtmlRead() throws Exception {
        res3.read(in3, true);
        assertEquals(200, res3.getStatusCode());
        assertTrue(res3.isHTTP11());
        assertTrue(res3.isKeepAlive());
        assertNotNull(res3.getBody());
    }


    @Test
    public void testChunkedHtmlWrite() throws Exception {
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
            assertEquals(res3.getBody().getContent().length, 0);

    }

    @Test
    void testWithNoContentLength() throws Exception {
        res3.read(getResourceAsStream(this,"response-no-content-length.txt"), true);
        assertEquals(185, res3.getBody().getLength());
    }

    @Test
    void isEmpty() throws IOException {
        assertTrue(ok().build().isBodyEmpty());
    }

    @Test
    public void isNotEmpty() throws Exception {
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
            of(ok(), 200, "Ok", false),
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
            of(statusCode(999), 999, "", false)
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
                <html><head><title>200 Ok.</title></head><body><h1>200 Ok.</h1><p>The Message <b>is</b> this!</p></body></html>""",
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
}
