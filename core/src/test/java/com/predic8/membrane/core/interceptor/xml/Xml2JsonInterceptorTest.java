/* Copyright 2021 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.xml;


import com.fasterxml.jackson.databind.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.router.*;
import org.apache.commons.io.*;
import org.jetbrains.annotations.*;
import org.junit.jupiter.api.*;
import org.slf4j.*;

import java.io.*;
import java.nio.charset.*;

import static com.predic8.membrane.core.http.MimeType.*;
import static java.nio.charset.StandardCharsets.*;
import static org.junit.jupiter.api.Assertions.*;


@SuppressWarnings({"UnnecessaryUnicodeEscape", "SpellCheckingInspection"})
public class Xml2JsonInterceptorTest {

    private static final Logger log = LoggerFactory.getLogger(Xml2JsonInterceptorTest.class);
    public static final String UMLAUTS = "\u00fc\u00f6\u00fc\u00f6\u00fc\u00f6";

    static Xml2JsonInterceptor interceptor;

    @BeforeAll
    static void setup() {
        interceptor = new Xml2JsonInterceptor();
        interceptor.init(new DefaultRouter());
    }

    @Test
    void invalidXml() {
        assertThrows(RuntimeException.class, () -> getJsonRootFromStream(processThroughInterceptor(fillAndGetExchange(
                new ByteArrayInputStream("<unclosed>".getBytes(UTF_8))))));
    }

    @Test
    void validTestxml2jsonWithEncodingInXml() throws Exception {
        assertEquals(UMLAUTS,
                getJsonRootFromStream(processThroughInterceptor(fillAndGetExchange(loadResource("/xml/content-utf-8-encoding-utf-8.xml"))))
                        .get("bar").get("futf").asText());
    }

    @Test
    void validTestEncodingInHeader() throws Exception {
        assertEquals(UMLAUTS,
                getJsonRootFromStream(processThroughInterceptor(fillAndGetExchangeWithEncodingHeader(loadResource("/xml/content-utf-8-encoding-without.xml"), TEXT_XML_UTF8))).get("bar").get("futf").asText());
    }

    @Test
    void encodingClashTest() throws Exception {
        assertNotEquals(UMLAUTS,
                getJsonRootFromStream(processThroughInterceptor(fillAndGetExchangeWithEncodingHeader(loadResource("/xml/content-utf-8-encoding-without.xml"),
                        TEXT_XML_ISO_8859_1))).get("bar").get("futf").asText());

    }

    @Test
    void isoEncoded() throws Exception {
        assertEquals(UMLAUTS,
                getJsonRootFromStream(processThroughInterceptor(fillAndGetExchangeWithEncodingHeader(changeEncoding(loadResource("/xml/content-utf-8-encoding-without.xml"), UTF_8, ISO_8859_1),
                        TEXT_XML_ISO_8859_1))).get("bar").get("futf").asText());

    }

    @Test
    void iso88591EncodedWithoutEncodingInHTTPHeader() throws Exception {
        assertEquals(UMLAUTS,
                getJsonRootFromStream(processThroughInterceptor(
                        fillAndGetExchangeWithEncodingHeader(loadResource("/xml/content-utf-8-encoding-iso-8859-1.xml"), TEXT_XML)))
                        .get("bar").get("futf").asText());
    }

    @Test
    void validTestxml2jsonResponse() throws Exception {
        assertEquals(UMLAUTS,
                getJsonRootFromStream(processThroughInterceptorResponse(loadResource("/xml/content-utf-8-encoding-utf-8.xml"))).get("bar").get("futf").asText());
    }

    private JsonNode getJsonRootFromStream(InputStream stream) throws IOException {
        return new ObjectMapper().readTree(stream);
    }

    private Request createRequestFromBytes(byte[] bytes) {
        return new Request.Builder().contentType(MimeType.TEXT_XML).body(bytes).build();
    }

    private Response createResponseFromBytes(byte[] bytes) {
        return new Response.ResponseBuilder().contentType(MimeType.TEXT_XML).body(bytes).build();
    }

    private InputStream processThroughInterceptor(Exchange exc) {
        Outcome outcome = interceptor.handleRequest(exc);
        if (outcome == Outcome.ABORT) {
            log.error("Interceptor aborted");
            log.error("Request: {}", exc.getRequest().getBodyAsStringDecoded());
            log.error("Response: {}", exc.getResponse().getBodyAsStringDecoded());
            throw new RuntimeException("Aborted");
        }
        return exc.getRequest().getBodyAsStream();
    }

    private InputStream processThroughInterceptorResponse(InputStream stream) throws Exception {
        Exchange exc = fillAndGetExchange(stream);
        interceptor.handleResponse(exc);
        return exc.getResponse().getBodyAsStream();
    }

    private InputStream loadResource(String path) {
        return this.getClass().getResourceAsStream(path);
    }

    private Exchange fillAndGetExchange(InputStream stream) throws IOException {
        return getExchangeFromStream(stream);
    }

    private Exchange fillAndGetExchangeWithEncodingHeader(InputStream stream, String contentTypeHeader) throws IOException {
        Exchange exc = getExchangeFromStream(stream);
        exc.getRequest().getHeader().setContentType(contentTypeHeader);
        exc.getResponse().getHeader().setContentType(contentTypeHeader);
        return exc;
    }

    private Exchange getExchangeFromStream(InputStream stream) throws IOException {
        Exchange exc = new Exchange(null);
        byte[] bytes = IOUtils.toByteArray(stream);
        exc.setRequest(createRequestFromBytes(bytes));
        exc.setResponse(createResponseFromBytes(bytes));
        return exc;
    }

    private static InputStream changeEncoding(InputStream in, Charset sourceEncoding, Charset targetEncoding) throws IOException {
        String text = new String(in.readAllBytes(), sourceEncoding);
        byte[] outBytes = text.getBytes(targetEncoding);
        return new ByteArrayInputStream(outBytes);
    }

}
