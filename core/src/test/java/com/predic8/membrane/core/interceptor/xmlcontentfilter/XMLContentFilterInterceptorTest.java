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
package com.predic8.membrane.core.interceptor.xmlcontentfilter;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.DecodingException;
import com.predic8.membrane.core.http.ReadingBodyException;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.router.DummyTestRouter;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.zip.GZIPOutputStream;

import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.test.StringAssertions.assertContainsNot;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

class XMLContentFilterInterceptorTest {

    private static final String DOCTYPE_BODY = "<!DOCTYPE a><a><secret>s3cr3t</secret><keep>ok</keep></a>";

    /** The entity reference comes before the element to remove. */
    private static final String DOCTYPE_ENTITY_BODY = "<!DOCTYPE a [<!ENTITY e \"x\">]><a>&e;<secret>s3cr3t</secret><keep>ok</keep></a>";

    private static XMLContentFilterInterceptor interceptor(String xPath) {
        final var interceptor = new XMLContentFilterInterceptor();
        interceptor.setXPath(xPath);
        interceptor.init(new DummyTestRouter());
        return interceptor;
    }

    private static Exchange requestExchange(String body) throws Exception {
        final var exc = new Exchange(null);
        exc.setRequest(new Request.Builder().post("/foo").contentType("application/xml").body(body).build());
        return exc;
    }

    /**
     * ProblemDetails looks at the request to choose between XML and JSON.
     */
    private static Exchange responseExchange(String body) throws Exception {
        final var exc = requestExchange("<a/>");
        exc.setResponse(Response.ok().contentType("application/xml").body(body).build());
        return exc;
    }

    @ParameterizedTest
    @ValueSource(strings = {"//secret", "/a/secret"})
    void removesElement(String xPath) throws Exception {
        final var exc = requestExchange("<a><secret>s3cr3t</secret><keep>ok</keep></a>");
        assertEquals(CONTINUE, interceptor(xPath).handleRequest(exc));
        assertContainsNot("s3cr3t", exc.getRequest().getBodyAsStringDecoded());
    }

    @ParameterizedTest
    @ValueSource(strings = {"//secret", "/a/secret"})
    void doctypeInRequestIsRejected(String xPath) throws Exception {
        final var exc = requestExchange(DOCTYPE_BODY);
        assertEquals(ABORT, interceptor(xPath).handleRequest(exc));
        assertEquals(400, exc.getResponse().getStatusCode());
        assertEquals("application/problem+xml", exc.getResponse().getHeader().getContentType());
        assertContainsNot("s3cr3t", exc.getResponse().getBodyAsStringDecoded());
    }

    @ParameterizedTest
    @ValueSource(strings = {"//secret", "/a/secret"})
    void doctypeWithEntityReferenceInRequestIsRejected(String xPath) throws Exception {
        final var exc = requestExchange(DOCTYPE_ENTITY_BODY);
        assertEquals(ABORT, interceptor(xPath).handleRequest(exc));
        assertEquals(400, exc.getResponse().getStatusCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {"//secret", "/a/secret"})
    void doctypeInResponseIsRejected(String xPath) throws Exception {
        final var exc = responseExchange(DOCTYPE_BODY);
        assertEquals(ABORT, interceptor(xPath).handleResponse(exc));
        assertEquals(500, exc.getResponse().getStatusCode());
        assertEquals("application/problem+xml", exc.getResponse().getHeader().getContentType());
        assertContainsNot("s3cr3t", exc.getResponse().getBodyAsStringDecoded());
    }

    @ParameterizedTest
    @ValueSource(strings = {"//secret", "/a/secret"})
    void emptyBodyIsLeftUnchanged(String xPath) throws Exception {
        final var exc = new Exchange(null);
        exc.setRequest(new Request.Builder().get("/foo").build());
        assertEquals(CONTINUE, interceptor(xPath).handleRequest(exc));
    }

    @ParameterizedTest
    @ValueSource(strings = {"//secret", "/a/secret"})
    void emptyResponseIsLeftUnchanged(String xPath) {
        final var exc = new Exchange(null);
        exc.setResponse(Response.noContent().build());
        assertEquals(CONTINUE, interceptor(xPath).handleResponse(exc));
    }

    @ParameterizedTest
    @ValueSource(strings = {"//secret", "/a/secret"})
    void nonXmlRequestIsRejected(String xPath) throws Exception {
        final var exc = requestExchange("{\"secret\":\"s3cr3t\"}");
        assertEquals(ABORT, interceptor(xPath).handleRequest(exc));
        assertEquals(400, exc.getResponse().getStatusCode());
    }

    /**
     * The first body matches the StAX pre-check of //secret before the error, the second has to be read to its end.
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "//secret  | <a><secret>s3cr3t</secret><keep>ok</keep>",
            "/a/secret | <a><secret>s3cr3t</secret><keep>ok</keep>",
            "//secret  | <a><keep>ok</keep>",
            "/a/secret | <a><keep>ok</keep>"})
    void malformedRequestIsRejected(String xPath, String body) throws Exception {
        final var exc = requestExchange(body);
        assertEquals(ABORT, interceptor(xPath).handleRequest(exc));
        assertEquals(400, exc.getResponse().getStatusCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {"//secret", "/a/secret"})
    void nonXmlResponseIsRejected(String xPath) throws Exception {
        final var exc = requestExchange("<a/>");
        exc.setResponse(Response.internalServerError().contentType("text/html").body("<html><body>at com.example.Foo(Foo.java:1)").build());
        assertEquals(ABORT, interceptor(xPath).handleResponse(exc));
        assertEquals(500, exc.getResponse().getStatusCode());
        assertContainsNot("Foo.java", exc.getResponse().getBodyAsStringDecoded());
    }

    /**
     * {@code //secret} is checked with the StAX pre-check first, the {@code local-name()} expression cannot be,
     * so it goes straight to the DOM parser.
     */
    @ParameterizedTest
    @ValueSource(strings = {"//secret", "/a/secret", "//*[local-name()='secret']"})
    void truncatedGzipRequestIsForwarded(String xPath) throws Exception {
        final var exc = new Exchange(null);
        exc.setRequest(new Request.Builder().post("/foo").contentType("application/xml; charset=UTF-8")
                .body(truncatedGzip("<a><secret>s3cr3t</secret><keep>ok</keep></a>"))
                .header("Content-Encoding", "gzip") // after body(), which clears Content-Encoding
                .build());
        final var failure = assertThrows(ReadingBodyException.class, () -> interceptor(xPath).handleRequest(exc));
        assertNull(exc.getResponse());
        assertEquals("gzip", assertInstanceOf(DecodingException.class, failure.getCause()).getContentEncoding());
    }

    @ParameterizedTest
    @ValueSource(strings = {"//secret", "/a/secret", "//*[local-name()='secret']"})
    void unreadableResponseIsForwarded(String xPath) throws Exception {
        final var exc = requestExchange("<a/>");
        exc.setResponse(Response.ok().contentType("application/xml").body(new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("connection reset");
            }
        }, false).build());
        assertThrows(ReadingBodyException.class, () -> interceptor(xPath).handleResponse(exc));
        assertEquals(200, exc.getResponse().getStatusCode());
    }

    /**
     * Keeps the 10-byte gzip header plus two bytes of compressed data, so decoding starts and only the first read
     * runs out of input.
     */
    private static byte[] truncatedGzip(String document) throws IOException {
        final var compressed = new ByteArrayOutputStream();
        try (var gzip = new GZIPOutputStream(compressed)) {
            gzip.write(document.getBytes(UTF_8));
        }
        return Arrays.copyOf(compressed.toByteArray(), 12);
    }
}
