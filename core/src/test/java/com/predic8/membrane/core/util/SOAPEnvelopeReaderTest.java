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
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.multipart.XOPReconstitutor;
import com.predic8.membrane.core.util.SOAPUtil.SOAPAnalysisResult;
import com.predic8.membrane.core.util.SOAPUtil.SOAPEnvelopeValidation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.xml.sax.SAXParseException;

import javax.xml.namespace.QName;

import static com.predic8.membrane.core.http.MimeType.TEXT_XML_UTF8;
import static com.predic8.membrane.core.http.Request.post;
import static org.junit.jupiter.api.Assertions.*;

class SOAPEnvelopeReaderTest {

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
     * Without {@code wholeMessage}, reading stops at the body element, so what follows is not seen.
     */
    @Test
    void analysisStopsAtBodyElement() throws Exception {
        final var result = analyse("""
                <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
                  <soapenv:Body><req:Request xmlns:req="http://example.com/svc">
                """);

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

    /**
     * A message is recognized as SOAP or not the same way, whether it is read as a whole or not.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "<x:Envelope xmlns:x=\"https://example.com/x\"><x:Body/></x:Envelope>",
            "<soapenv:Body xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\"/>",
            "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\"><x:Foo xmlns:x=\"https://example.com/x\"/><soapenv:Body/></soapenv:Envelope>",
            "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\"><soapenv:Foo/><soapenv:Body/></soapenv:Envelope>",
            "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\"><soapenv:Body>foo</soapenv:Body></soapenv:Envelope>",
            "not XML"
    })
    void notSoap(String message) throws Exception {
        assertFalse(analyse(message).isSOAP());
        final var result = validateEnvelope(message);
        assertFalse(result.analysis().isSOAP());
        assertTrue(result.errors().isEmpty(), result.errors()::toString);
    }

    /**
     * An unqualified {@code Fault} is taken as a fault as well.
     */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void unqualifiedFault(boolean wholeMessage) throws Exception {
        final var result = SOAPEnvelopeReader.read(new XOPReconstitutor(), message("""
                <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
                  <soapenv:Body><Fault><faultcode>Client</faultcode><faultstring>failed</faultstring></Fault></soapenv:Body>
                </soapenv:Envelope>
                """), wholeMessage);

        assertTrue(result.analysis().isSOAP());
        assertTrue(result.analysis().isFault());
        assertNull(result.analysis().soapElement());
        assertTrue(result.errors().isEmpty(), result.errors()::toString);
    }

    private static SOAPEnvelopeValidation assertSingleError(String soap, String message, int line) throws Exception {
        final var result = validateEnvelope(soap);
        assertTrue(result.analysis().isSOAP());
        assertEquals(1, result.errors().size(), result.errors()::toString);
        final var error = assertInstanceOf(SAXParseException.class, result.errors().getFirst());
        if (message != null)
            assertEquals(message, error.getMessage());
        assertEquals(line, error.getLineNumber());
        return result;
    }

    private static SOAPEnvelopeValidation validateEnvelope(String soap) throws Exception {
        return SOAPEnvelopeReader.read(new XOPReconstitutor(), message(soap), true);
    }

    private static SOAPAnalysisResult analyse(String soap) throws Exception {
        return SOAPEnvelopeReader.read(new XOPReconstitutor(), message(soap), false).analysis();
    }

    private static Message message(String soap) throws Exception {
        return post("/").contentType(TEXT_XML_UTF8).body(soap).buildExchange().getRequest();
    }
}
