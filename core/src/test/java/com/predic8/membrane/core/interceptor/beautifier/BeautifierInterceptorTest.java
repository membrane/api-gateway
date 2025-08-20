/*
 *  Copyright 2023 predic8 GmbH, www.predic8.com
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package com.predic8.membrane.core.interceptor.beautifier;

import com.fasterxml.jackson.databind.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import org.jetbrains.annotations.*;
import org.junit.jupiter.api.*;

import java.net.*;
import java.nio.charset.*;

import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.http.Request.*;
import static com.predic8.membrane.core.http.Response.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.util.xml.XMLEncodingTestUtil.*;
import static java.nio.charset.StandardCharsets.*;
import static org.junit.jupiter.api.Assertions.*;

class BeautifierInterceptorTest {

    static final ObjectMapper om = new ObjectMapper();

    BeautifierInterceptor interceptor;

    Exchange jsonExc;
    Exchange xmlExc;

    Response response;
    
    JsonNode json;
    static final byte[] xml = ("<foo><bar>baz</bar></foo>").getBytes(UTF_8);

    @BeforeEach
    void setUp() throws Exception {
        json = om.readTree("{\"test\": \"foo\", \"sad\": \"sad\"}");

        interceptor = new BeautifierInterceptor();
        jsonExc = post("/foo").contentType(APPLICATION_JSON).buildExchange();
        xmlExc = post("/foo").contentType(APPLICATION_XML).buildExchange();
        response = ok().contentType(TEXT_PLAIN).body("Message").build();
    }

    @Test
    void empty() throws URISyntaxException {
        assertEquals(CONTINUE,interceptor.handleRequest(get("/foo").buildExchange()));
    }

    @Test
    void jsonRequest() throws Exception {
        Exchange exc = post("/foo")
                .contentType(APPLICATION_JSON)
                .body(jsonString())
                .buildExchange();
        
        interceptor.handleRequest(exc);

        Request req = exc.getRequest();
        int bodyLength = req.getBody().getLength();
        assertEquals(req.getHeader().getContentLength(), bodyLength);
        assertEquals(bodyLength, req.getBodyAsStringDecoded().length());
    }

    @Test
    void jsonResponse() throws Exception {
        Exchange exc = Response.ok().body(jsonString()).buildExchange();

        interceptor.handleResponse(exc);

        Response res = exc.getResponse();
        int bodyLength = res.getBody().getLength();
        assertEquals(res.getHeader().getContentLength(), bodyLength);
        assertEquals(bodyLength, res.getBodyAsStringDecoded().length());
    }

    private static @NotNull String jsonString() {
        return """
                { "place": "Mumbai", "foo": {"bar": "baz", "sad": 5 } }
                """;
    }

    @Test
    void JSONBeautifierTest () throws Exception {
        Request req = jsonExc.getRequest();
        req.setBodyContent(om.writeValueAsBytes(json));
        jsonExc.setRequest(req);
        assertFalse(jsonExc.getRequest().getBody().toString().contains("\n"));
        interceptor.handleRequest(jsonExc);
        assertTrue(jsonExc.getRequest().getBody().toString().contains("\n"));
    }

    @Test
    void XMLBeautifierTest() {
        Request req = xmlExc.getRequest();
        req.setBodyContent(xml);
        xmlExc.setRequest(req);
        assertFalse(xmlExc.getRequest().getBody().toString().contains("\n"));
        interceptor.handleRequest(xmlExc);
        assertTrue(xmlExc.getRequest().getBody().toString().contains("\n"));
    }

    @Nested
    class Encoding {

        @Test
        void isoCharsetInHttpHeader() throws Exception {
            checkForFile("/charsets/iso-8859-1-unformatted.xml", ISO_8859_1, "iso-8859-1");
        }

        @Test
        void isoWithoutEncodingInfoInTheHeader() throws Exception {
            checkForFile("/charsets/iso-8859-1-unformatted.xml", ISO_8859_1,null);
        }

        @Test
        void utf8WithoutEncodingInfoInTheHeader() throws Exception {
            checkForFile("/charsets/utf-8-unformatted.xml", UTF_8,null);
        }

        @Test
        void utf16BEWithoutEncodingInfoInTheHeader() throws Exception {
            checkForFile("/charsets/utf-16be-unformatted.xml", UTF_16,null);
        }

        private void checkForFile(String file, Charset expectedCharset, String contentTypeCharset) throws Exception {
            Exchange exc = post("/foo")
                    .contentType(getContentType(contentTypeCharset))
                    .body(getClass().getResourceAsStream(file))
                    .buildExchange();

            interceptor.handleRequest(exc);

            String text = new String(exc.getRequest().getBodyAsStream().readAllBytes(), expectedCharset);

            assertChars(text );
        }

        private static String getContentType(String contentTypeCharset) {
            String contentType = APPLICATION_XML;
            if (contentTypeCharset != null) {
                return contentType + "; charset=" + contentTypeCharset;
            }
            return contentType;
        }
    }
}
