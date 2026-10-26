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

import com.predic8.membrane.annot.Constants.SoapVersion;
import com.predic8.membrane.core.http.DecodingException;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.http.ReadingBodyException;
import com.predic8.membrane.core.multipart.XOPReconstitutor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXParseException;

import javax.xml.namespace.QName;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.zip.GZIPOutputStream;

import static com.predic8.membrane.annot.Constants.SOAP11_NS;
import static com.predic8.membrane.annot.Constants.SOAP12_NS;
import static com.predic8.membrane.core.http.MimeType.TEXT_XML_UTF8;
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

    @Test
    void validEnvelope() throws Exception {
        final var result = validateEnvelope("""
                <soapenv:Envelope xmlns:soapenv="http://www.w3.org/2003/05/soap-envelope">
                  <soapenv:Header><h:Block xmlns:h="http://example.com/h"/></soapenv:Header>
                  <soapenv:Body><req:Request xmlns:req="http://example.com/svc"/></soapenv:Body>
                </soapenv:Envelope>
                """);

        assertTrue(result.analysis().isSOAP());
        assertFalse(result.analysis().isFault());
        assertEquals(SoapVersion.SOAP12, result.analysis().version());
        assertEquals(new QName("http://example.com/svc", "Request"), result.analysis().soapElement());
        assertTrue(result.errors().isEmpty(), result.errors()::toString);
    }

    @Test
    void validEmptyBody() throws Exception {
        final var result = validateEnvelope("""
                <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
                  <soapenv:Body/>
                </soapenv:Envelope>
                """);

        assertTrue(result.analysis().isSOAP());
        assertNull(result.analysis().soapElement());
        assertTrue(result.errors().isEmpty(), result.errors()::toString);
    }

    @Test
    void headerAfterBody() throws Exception {
        assertSingleError("""
                <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
                  <soapenv:Body><req:Request xmlns:req="http://example.com/svc"/></soapenv:Body>
                  <soapenv:Header><h:Block xmlns:h="http://example.com/h"/></soapenv:Header>
                </soapenv:Envelope>
                """, "The SOAP Header must come before the Body.", 3);
    }

    @Test
    void secondHeader() throws Exception {
        assertSingleError("""
                <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
                  <soapenv:Header><h:Block xmlns:h="http://example.com/h"/></soapenv:Header>
                  <soapenv:Header><h:Block xmlns:h="http://example.com/h"/></soapenv:Header>
                  <soapenv:Body><req:Request xmlns:req="http://example.com/svc"/></soapenv:Body>
                </soapenv:Envelope>
                """, "The SOAP Envelope must not contain more than one Header.", 3);
    }

    @Test
    void secondBody() throws Exception {
        assertSingleError("""
                <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
                  <soapenv:Body/>
                  <soapenv:Body><req:Request xmlns:req="http://example.com/svc"/></soapenv:Body>
                </soapenv:Envelope>
                """, "The SOAP Envelope must not contain more than one Body.", 3);
    }

    @Test
    void secondHeaderOfFault() throws Exception {
        final var result = assertSingleError("""
                <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
                  <soapenv:Header/>
                  <soapenv:Header/>
                  <soapenv:Body><soapenv:Fault/></soapenv:Body>
                </soapenv:Envelope>
                """, "The SOAP Envelope must not contain more than one Header.", 3);

        assertTrue(result.analysis().isFault());
    }

    /**
     * SOAP 1.1 allows further elements after the body. Only the SOAP header as a child of the
     * envelope counts.
     */
    @Test
    void headerNestedInElementAfterBody() throws Exception {
        assertTrue(validateEnvelope("""
                <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
                  <soapenv:Body><req:Request xmlns:req="http://example.com/svc"/></soapenv:Body>
                  <t:Trailer xmlns:t="http://example.com/t"><soapenv:Header/></t:Trailer>
                </soapenv:Envelope>
                """).errors().isEmpty());
    }

    @Test
    void elementAfterBodyInSoap12() throws Exception {
        assertSingleError("""
                <soapenv:Envelope xmlns:soapenv="http://www.w3.org/2003/05/soap-envelope">
                  <soapenv:Body><req:Request xmlns:req="http://example.com/svc"/></soapenv:Body>
                  <t:Trailer xmlns:t="http://example.com/t"/>
                </soapenv:Envelope>
                """, "A SOAP 1.2 Envelope must not contain an element after the Body.", 3);
    }

    @Test
    void headerOfOtherSoapVersion() throws Exception {
        assertSingleError("""
                <soapenv:Envelope xmlns:soapenv="http://www.w3.org/2003/05/soap-envelope">
                  <s11:Header xmlns:s11="http://schemas.xmlsoap.org/soap/envelope/"/>
                  <soapenv:Body/>
                </soapenv:Envelope>
                """, "The SOAP Header must be in the namespace of the Envelope, http://www.w3.org/2003/05/soap-envelope.", 2);
    }

    /**
     * The parse error is reported with its location, as the SAX parser reports it.
     */
    @Test
    void truncatedBody() throws Exception {
        final var error = assertSingleError("""
                <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
                  <soapenv:Body><req:Request xmlns:req="http://example.com/svc">
                """, null, 3).errors().getFirst();

        assertFalse(error.getMessage().contains("row,col"), error.getMessage());
    }

    @Test
    void undeclaredEntityInHeader() throws Exception {
        assertSingleError("""
                <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
                  <soapenv:Header><h:Block xmlns:h="http://example.com/h">&undeclared;</h:Block></soapenv:Header>
                  <soapenv:Body/>
                </soapenv:Envelope>
                """, null, 2);
    }

    @Test
    void invalidCharacterInBody() throws Exception {
        assertSingleError("""
                <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
                  <soapenv:Body><req:Request xmlns:req="http://example.com/svc">&#0;</req:Request></soapenv:Body>
                </soapenv:Envelope>
                """, null, 2);
    }

    /**
     * {@link SOAPUtil#analyseSOAPMessage} stops at the body element, so it does not see what follows.
     */
    @Test
    void analysisStopsAtBodyElement() throws Exception {
        final var result = SOAPUtil.analyseSOAPMessage(new XOPReconstitutor(), message("""
                <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
                  <soapenv:Body><req:Request xmlns:req="http://example.com/svc">
                """));

        assertTrue(result.isSOAP());
        assertEquals(new QName("http://example.com/svc", "Request"), result.soapElement());
    }

    @Test
    void malformedBeforeBodyIsNotSoap() throws Exception {
        final var result = validateEnvelope("""
                <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
                  <soapenv:Header><a></b></soapenv:Header>
                  <soapenv:Body/>
                </soapenv:Envelope>
                """);

        assertFalse(result.analysis().isSOAP());
        assertTrue(result.errors().isEmpty());
    }

    @Test
    void envelopeWithoutBodyIsNotSoap() throws Exception {
        assertFalse(validateEnvelope("""
                <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
                  <soapenv:Header/>
                </soapenv:Envelope>
                """).analysis().isSOAP());
    }

    private static SOAPUtil.SOAPEnvelopeValidation assertSingleError(String soap, String message, int line) throws Exception {
        final var result = validateEnvelope(soap);
        assertTrue(result.analysis().isSOAP());
        assertEquals(1, result.errors().size(), result.errors()::toString);
        final var error = assertInstanceOf(SAXParseException.class, result.errors().getFirst());
        if (message != null)
            assertEquals(message, error.getMessage());
        assertEquals(line, error.getLineNumber());
        return result;
    }

    private static SOAPUtil.SOAPEnvelopeValidation validateEnvelope(String soap) throws Exception {
        return SOAPUtil.validateSOAPEnvelope(new XOPReconstitutor(), message(soap));
    }

    private static Message message(String soap) throws Exception {
        return post("/").contentType(TEXT_XML_UTF8).body(soap).buildExchange().getRequest();
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
