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

package com.predic8.membrane.core.util;

import com.predic8.membrane.core.http.DecodingException;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.http.ReadingBodyException;
import com.predic8.membrane.core.multipart.XOPReconstitutor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import static com.predic8.membrane.annot.Constants.PRODUCT_NAME;
import static com.predic8.membrane.annot.Constants.SOAP11_NS;
import static com.predic8.membrane.annot.Constants.SOAP12_NS;
import static com.predic8.membrane.annot.Constants.SoapVersion.SOAP11;
import static com.predic8.membrane.annot.Constants.SoapVersion.SOAP12;
import static com.predic8.membrane.core.http.Header.SERVER;
import static com.predic8.membrane.core.http.MimeType.TEXT_XML_UTF8;
import static com.predic8.membrane.core.util.SOAPUtil.FaultCode.Client;
import static com.predic8.membrane.core.util.SOAPUtil.FaultCode.Server;
import static com.predic8.membrane.core.http.Request.post;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

class SOAPUtilTest {

    /**
     * Both sniffs answer "is this SOAP?" by reading the body, so a body they could not read tells them
     * nothing. Reporting it as "not SOAP" would turn a transport or Content-Encoding failure into a
     * verdict on content that was never seen - and downstream that reads as a plain non-SOAP message,
     * with no sign that anything went wrong.
     */
    @Test
    @DisplayName("analyseSOAPMessage passes an unreadable body on instead of reporting it as not SOAP")
    void analyseSOAPMessageRethrowsUnreadableBody() throws Exception {
        ReadingBodyException failure = assertThrows(ReadingBodyException.class,
                () -> SOAPUtil.analyseSOAPMessage(new XOPReconstitutor(), undecodableBody()));

        assertEquals("gzip", assertInstanceOf(DecodingException.class, failure.getCause()).getContentEncoding());
    }

    @Test
    @DisplayName("isSOAP passes an unreadable body on instead of reporting it as not SOAP")
    void isSOAPRethrowsUnreadableBody() throws Exception {
        assertThrows(ReadingBodyException.class,
                () -> SOAPUtil.isSOAP(new XOPReconstitutor(), undecodableBody()));
    }

    @Test
    void soapNamespaces() {
        assertTrue(SOAPUtil.isSOAPNamespace(SOAP11_NS));
        assertTrue(SOAPUtil.isSOAPNamespace(SOAP12_NS));
        assertFalse(SOAPUtil.isSOAPNamespace("https://example.com/envelope"));
        assertFalse(SOAPUtil.isSOAPNamespace(""));
    }

    @Test
    @DisplayName("validateSOAPEnvelope passes an unreadable body on instead of reporting it as not SOAP")
    void validateSOAPEnvelopeRethrowsUnreadableBody() throws Exception {
        assertThrows(ReadingBodyException.class,
                () -> SOAPUtil.validateSOAPEnvelope(new XOPReconstitutor(), undecodableBody()));
    }

    /** SOAP 1.1 section 6.2: every fault is sent with 500. */
    @Test
    void soap11FaultResponse() {
        final var response = SOAPUtil.createSOAPFaultResponse(Client, "rejected", Map.of(), SOAP11);
        assertEquals(500, response.getStatusCode());
        assertEquals("Internal Server Error", response.getStatusMessage());
        assertEquals(TEXT_XML_UTF8, response.getHeader().getContentType());
        assertEquals(PRODUCT_NAME, response.getHeader().getFirstValue(SERVER));
        assertTrue(response.getBodyAsStringDecoded().contains(SOAP11_NS));
    }

    @Test
    void soap11ServerFaultResponse() {
        assertEquals(500, SOAPUtil.createSOAPFaultResponse(Server, "failed", Map.of(), SOAP11).getStatusCode());
    }

    /**
     * SOAP 1.2 Part 2, section 7.5.2.2: a Sender fault is sent with 400. SOAP 1.2 is sent as
     * application/soap+xml (section 7.1.4); a SOAP 1.2 client may reject a text/xml response.
     */
    @Test
    void soap12SenderFaultResponse() {
        final var response = SOAPUtil.createSOAPFaultResponse(Client, "rejected", Map.of(), SOAP12);
        assertEquals(400, response.getStatusCode());
        assertEquals("Bad Request", response.getStatusMessage());
        assertEquals("application/soap+xml;charset=UTF-8", response.getHeader().getContentType());
        assertEquals(PRODUCT_NAME, response.getHeader().getFirstValue(SERVER));
        assertTrue(response.getBodyAsStringDecoded().contains(SOAP12_NS));
    }

    /** SOAP 1.2 Part 2, section 7.5.2.2: every fault but a Sender fault is sent with 500. */
    @Test
    void soap12ReceiverFaultResponse() {
        final var response = SOAPUtil.createSOAPFaultResponse(Server, "failed", Map.of(), SOAP12);
        assertEquals(500, response.getStatusCode());
        assertEquals("Internal Server Error", response.getStatusMessage());
    }

    /** Announced as gzip, but the compressed data stops two bytes into the stream. */
    private static Message undecodableBody() throws Exception {
        var compressed = new ByteArrayOutputStream();
        try (var gzip = new GZIPOutputStream(compressed)) {
            gzip.write("<soapenv:Envelope/>".getBytes(UTF_8));
        }
        return post("/")
                .contentType(TEXT_XML_UTF8)
                .body(truncate(compressed))
                .header("Content-Encoding", "gzip") // after body(), which clears Content-Encoding
                .buildExchange()
                .getRequest();
    }

    private static byte[] truncate(ByteArrayOutputStream compressed) throws IOException {
        return Arrays.copyOf(compressed.toByteArray(), 12); // the 10-byte header plus two bytes
    }
}
