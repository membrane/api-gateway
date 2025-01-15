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
import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import org.apache.commons.io.*;
import org.junit.jupiter.api.*;

import java.io.*;

import static java.nio.charset.StandardCharsets.*;
import static org.junit.jupiter.api.Assertions.*;


@SuppressWarnings({"UnnecessaryUnicodeEscape", "SpellCheckingInspection"})
public class Xml2JsonInterceptorTest {

    static Xml2JsonInterceptor interceptor;

    @BeforeAll
    static void setup() {
        interceptor = new Xml2JsonInterceptor();
        interceptor.init(new Router());
    }

    @Test
    public void invalidXml() throws Exception {
        assertThrows(RuntimeException.class, () -> getJsonRootFromStream(processThroughInterceptor(fillAndGetExchange(
                new ByteArrayInputStream("5".getBytes(UTF_8))))));
    }

    @Test
    public void validTestxml2jsonWithEncodingInXml() throws Exception {
        assertEquals("\u00fc\u00f6\u00fc\u00f6\u00fc\u00f6",
                getJsonRootFromStream(processThroughInterceptor(fillAndGetExchange(loadResource("/xml/convert.xml"))))
                        .get("bar").get("futf").asText());
    }

    @Test
    public void validTestEncodingInHeader() throws Exception {
        assertEquals("\u00fc\u00f6\u00fc\u00f6\u00fc\u00f6",
                getJsonRootFromStream(processThroughInterceptor(fillAndGetExchangewithEncodingHeader(loadResource("/xml/convert_without_encoding.xml"),
                        Constants.ISO_8859_1))).get("bar").get("futf").asText());
    }

    @Test
    public void encodingClashTest() throws Exception {
        assertNotEquals("\u00fc\u00f6\u00fc\u00f6\u00fc\u00f6",
                getJsonRootFromStream(processThroughInterceptor(fillAndGetExchangewithEncodingHeader(loadResource("/xml/convert.xml"),
                        Constants.ISO_8859_1))).get("bar").get("futf").asText());

    }

    @Test
    public void validTestxml2jsonResponse() throws Exception {
        //\u00fc\u00f6\u00fc\u00f6\u00fc\u00f6 = ������
        assertEquals("\u00fc\u00f6\u00fc\u00f6\u00fc\u00f6",
                getJsonRootFromStream(processThroughInterceptorResponse(loadResource("/xml/convert.xml"))).get("bar").get("futf").asText());
    }

    private JsonNode getJsonRootFromStream(InputStream stream) throws IOException {
        return new ObjectMapper().readTree(stream);
    }

    private Request createRequestFromBytes(byte[] bytes) throws IOException {
        return new Request.Builder().contentType(MimeType.TEXT_XML).body(bytes).build();
    }

    private Response createResponseFromBytes(byte[] bytes) throws IOException {
        return new Response.ResponseBuilder().contentType(MimeType.TEXT_XML).body(bytes).build();
    }

    private InputStream processThroughInterceptor(Exchange exc) throws Exception {
        Outcome outcome = interceptor.handleRequest(exc);
        if (outcome == Outcome.ABORT) {
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

    private Exchange fillAndGetExchangewithEncodingHeader(InputStream stream, String encoding) throws IOException {
        Exchange exc = getExchangeFromStream(stream);
        exc.getRequest().getHeader().add(new HeaderField(Header.CONTENT_ENCODING, encoding));
        exc.getResponse().getHeader().add(new HeaderField(Header.CONTENT_ENCODING, encoding));
        return exc;
    }

    private Exchange getExchangeFromStream(InputStream stream) throws IOException {
        Exchange exc = new Exchange(null);
        byte[] bytes = IOUtils.toByteArray(stream);
        exc.setRequest(createRequestFromBytes(bytes));
        exc.setResponse(createResponseFromBytes(bytes));
        return exc;
    }

}
