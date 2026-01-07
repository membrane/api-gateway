/* Copyright 2009, 2012 predic8 GmbH, www.predic8.com

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

import java.io.*;
import java.net.*;

import static com.predic8.membrane.core.Constants.*;
import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.http.Request.*;
import static com.predic8.membrane.core.util.HttpTestUtil.*;
import static com.predic8.membrane.core.util.StringTestUtil.*;
import static com.predic8.membrane.test.TestUtil.*;
import static java.nio.charset.StandardCharsets.*;
import static org.junit.jupiter.api.Assertions.*;

public class RequestTest {

    private Request request;

    private InputStream getReq;
    private InputStream inPost;
    private InputStream inEmptyBody;
    private InputStream inEmptyBodyContentLength;
    private InputStream inEmptyBodyContentType;
    private InputStream inNoChunks;
    private InputStream inChunked;

    private ByteArrayOutputStream tempOut;

    private InputStream tempIn;

    private static final String GET = """
            GET /foo HTTP/1.1
            X-Bar: 42
            
            """;

    private static final String POST = """
            POST /operation/call HTTP/1.1
            Host: service-repository.com:80
            Connection: keep-alive
            Content-Length: 168
            Content-Type: application/x-www-form-urlencoded
            
            endpoint=http%3A%2F%2Fwww.thomas-bayer.com%3A80%2Faxis2%2Fservices%2FBLZService&xpath%3A%2FgetBank%2Fblz=38070024&id=65657&operation=getBank&portType=BLZServicePortType""";

    private static final String CHUNKED = """
            POST /axis2/services/BLZService HTTP/1.1
            Content-Type: application/soap+xml; charset=UTF-8; action="http://thomas-bayer.com/blz/BLZServicePortType/getBankRequest"
            Host: localhost:7000
            Transfer-Encoding: chunked
            
            ff
            <?xml version='1.0' encoding='UTF-8'?><soapenv:Envelope xmlns:soapenv="http://www.w3.org/2003/05/soap-envelope"><soapenv:Body><ns1:getBank xmlns:ns1="http://thomas-bayer.com/blz/"><ns1:blz>66762332</ns1:blz></ns1:getBank></soapenv:Body></soapenv:Envelope>
            0""";

    /**
     * No body related headers
     */
    private static final String EMPTY_BODY = """
            POST /operation/call HTTP/1.1
            Host: api.predic8.de:443
            
            """;

    private static final String EMPTY_BODY_CONTENT_LENGTH = """
            POST /operation/call HTTP/1.1
            Host: api.predic8.de:443
            Content-Length: 0
            
            """;

    private static final String EMPTY_BODY_CONTENT_TYPE = """
            POST /operation/call HTTP/1.1
            Host: api.predic8.de:443
            Content-Type: application/json
            
            """;

    // Trailing line is needed for chunked parsing
    @SuppressWarnings("TrailingWhitespacesInTextBlock")
    private static final String NO_CHUNKS = """
            POST /resource HTTP/1.1
            Host: example.com
            Transfer-Encoding: chunked
            
            0
                    
            """;

    @BeforeEach
    public void setUp() {
        request = new Request();
        getReq = convertMessage(GET);
        inPost = convertMessage(POST);
        inEmptyBody = convertMessage(EMPTY_BODY);
        inEmptyBodyContentLength = convertMessage(EMPTY_BODY_CONTENT_LENGTH);
        inEmptyBodyContentType = convertMessage(EMPTY_BODY_CONTENT_TYPE);
        inNoChunks = convertMessage(NO_CHUNKS);
        inChunked = convertMessage(CHUNKED);
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (getReq != null) {
            getReq.close();
        }
        if (inPost != null) {
            inPost.close();
        }
        if (inEmptyBody != null) {
            inEmptyBody.close();
        }
        if (inEmptyBodyContentLength != null) {
            inEmptyBodyContentLength.close();
        }
        if (inEmptyBodyContentType != null) {
            inEmptyBodyContentType.close();
        }
        if (inNoChunks != null) {
            inNoChunks.close();
        }
        if (inChunked != null) {
            inChunked.close();
        }
        if (tempIn != null) {
            tempIn.close();
        }
        if (tempOut != null) {
            tempOut.close();
        }

    }

    @Test
    void get() throws Exception {
        request.parseStartLine(getReq);
        assertInstanceOf(EmptyBody.class, request.getBody());
    }

    @Test
    void parseStartLineChunked() throws IOException {
        request.parseStartLine(inChunked);
        assertTrue(request.isPOSTRequest());
        assertEquals("/axis2/services/BLZService", request.getUri());
        assertEquals("1.1", request.getVersion());
    }

    @Test
    void readChunked() throws Exception {
        request.read(inChunked, true);
        assertNotNull(request.getBodyAsStream());
    }

    @Test
    void readPost() throws Exception {
        request.read(inPost, true);
        assertEquals(METHOD_POST, request.getMethod());
        assertEquals("/operation/call", request.getUri());
        assertNotNull(request.getBody());
        assertEquals(168, request.getBody().getLength());
    }

    @Test
    void noChunks() throws Exception {
        request.read(inNoChunks, true);
        assertNotNull(request.getBody());
        assertEquals(0, request.getBody().getLength());
    }


    @Test
    void emptyBody() throws Exception {
        testForEmptyBody(inEmptyBody);
    }

    @Test
    void emptyBodyContentLength() throws Exception {
        testForEmptyBody(inEmptyBodyContentLength);
    }

    @Test
    void emptyBodyContentType() throws Exception {
        testForEmptyBody(inEmptyBodyContentType);
    }

    private void testForEmptyBody(InputStream message) throws IOException, EndOfStreamException {
        request.read(message, true);
        assertEquals(METHOD_POST, request.getMethod());
        assertInstanceOf(EmptyBody.class, request.getBody());
    }

    @Test
    void writePost() throws Exception {
        request.read(inPost, true);

        tempOut = new ByteArrayOutputStream();
        request.write(tempOut, true);

        tempIn = new ByteArrayInputStream(tempOut.toByteArray());

        Request reqTemp = new Request();
        reqTemp.read(tempIn, true);

        assertEquals(request.getUri(), reqTemp.getUri());
        assertEquals(request.getMethod(), reqTemp.getMethod());

        assertArrayEquals(request.getBody().getContent(), reqTemp.getBody().getContent());
        assertArrayEquals(request.getBody().getRaw(), reqTemp.getBody().getRaw());
    }

    @Test
    void isHTTP11() {
        assertTrue(request.isHTTP11());
    }

    @Test
    void isHTTP11Chunked() {
        assertTrue(request.isHTTP11());
    }

    @Test
    void isKeepAlive() {
        assertTrue(request.isKeepAlive());
    }

    @Test
    void isKeepAliveChunked() {
        assertTrue(request.isKeepAlive());
    }

    @Test
    void isEmpty() throws IOException, URISyntaxException {
        assertTrue(new Builder().body("").build().isBodyEmpty());
        assertTrue(new Builder().body("".getBytes(UTF_8)).build().isBodyEmpty());
        assertTrue(Request.get("/foo").build().isBodyEmpty());
    }

    @Test
    void isNotEmpty() throws IOException, URISyntaxException {
        assertFalse(post("/foo").body("ABC").build().isBodyEmpty());
    }

    @Test
    void createFromStream() throws IOException {
        Header header = new Header();
        header.add("Content-Length", "1");
        var req = create("POST", "http://test", "HTTP/", header, getResourceAsStream(this, "/getBank.xml"));
        assertFalse(req.isBodyEmpty());
    }

    @Test
    void createFromStreamMethodGETDoNotSupportBody() throws IOException {
        var req = create(METHOD_GET, "http://test", "HTTP/", new Header(), getResourceAsStream(this, "/getBank.xml"));
        assertTrue(req.isBodyEmpty());
    }

    @Test
    void createFromStreamMethodHEADDoNotSupportBody() throws IOException {
        var req = create(METHOD_HEAD, "http://test", "HTTP/", new Header(), getResourceAsStream(this, "/getBank.xml"));
        assertTrue(req.isBodyEmpty());
    }

    @Test
    void addHeaderToExisting() throws IOException, EndOfStreamException {
        Request req = new Request();
        req.read(inputStreamFrom("""
                GET / HTTP/1.1
                Foo: 1
                Foo: 2
                
                """), true);
        req.getHeader().add("Foo", "3"); // Now add a third and see if the sequence is kept.

        assertEquals("1,2,3", req.getHeader().getNormalizedValue("Foo"));
    }

    /**
     * If we replace the body, the original body should be read, to make sure there is nothing left
     * in the inputStream that can be read as part of the next message in an keep alive session.
     */
    @Test
    void setBodyShouldReadTheOriginalBody() throws EndOfStreamException, IOException {
        AbstractBody originalBody = readMessageAndGetBody();
        request.setBody(new Body("ABC".getBytes(UTF_8))); // Replace body with a different one
        assertTrue(originalBody.isRead()); // Assert that the original body is read
    }

    @Test
    void optionsWithBodyContentLength() throws EndOfStreamException, IOException {
        shouldBodyBeRead("""
                OPTIONS /products HTTP/1.1
                Content-Length: 5
                Origin: https://predic8.de
                
                Dummy
                """, true);
    }

    @Test
    void optionsWithBody() throws EndOfStreamException, IOException {
        shouldBodyBeRead("""
                OPTIONS /products HTTP/1.1
                Transfer-Encoding: chunked
                Origin: https://predic8.de
                
                Dummy
                """, true);
    }

    @Test
    void optionsWithoutBody() throws EndOfStreamException, IOException {
        shouldBodyBeRead("""
                OPTIONS /products HTTP/1.1
                Origin: https://predic8.de
                
                """, false);
    }

    private static void shouldBodyBeRead(String message, boolean expect) throws IOException, EndOfStreamException {
        Request req = new Request();
        req.read(new ByteArrayInputStream(message.getBytes(UTF_8)), true);
        assertEquals(expect, !req.shouldNotContainBody());
    }

    /**
     * Same as setBodyShouldReadTheOriginalBody test but with Request.setBodyContent
     */
    @Test
    void setBodyContentShouldReadTheOriginalBody() throws EndOfStreamException, IOException {
        AbstractBody originalBody = readMessageAndGetBody();
        request.setBodyContent("ABC".getBytes(UTF_8));
        assertTrue(originalBody.isRead()); // Assert that the original body is read
        assertEquals(0, inPost.available()); // Check that all bytes are read from the stream
    }

    @Test
    void connectUsesAuthorityForm() throws URISyntaxException {
        assertEquals("CONNECT example.com:443 HTTP/1.1" + CRLF, connect("https://example.com:443").build().getStartLine());
    }

    @Test
    void isXML() throws URISyntaxException {
        assertTrue(post("/foo").contentType(TEXT_XML).build().isXML());
        assertTrue(post("/foo").contentType("text/xml; charset=utf-8").build().isXML());
        assertTrue(post("/foo").header("Content-Type", "text/xml; charset=utf-8").build().isXML());
    }

    private AbstractBody readMessageAndGetBody() throws IOException, EndOfStreamException {
        request.read(inPost, true);
        assertFalse(request.getBody().isRead());
        return request.getBody();
    }

    public Request create(String method, String uri, String protocol, Header header, InputStream in) throws IOException {
        var r = new Request();
        r.method = method;
        r.uri = uri;
        if (!protocol.startsWith("HTTP/"))
            throw new RuntimeException("Unknown protocol '" + protocol + "'");
        r.version = protocol.substring(5);
        r.header = header;

        r.createBody(in);
        return r;
    }
}