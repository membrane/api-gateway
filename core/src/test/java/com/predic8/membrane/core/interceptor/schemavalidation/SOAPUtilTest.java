/* Copyright 2011, 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor.schemavalidation;

import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.multipart.XOPReconstitutor;
import com.predic8.membrane.core.router.TestRouter;
import com.predic8.membrane.core.util.SOAPUtil;
import org.junit.jupiter.api.Test;

import javax.xml.namespace.QName;
import java.io.FileInputStream;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.predic8.membrane.annot.Constants.SoapVersion.SOAP11;
import static com.predic8.membrane.annot.Constants.SoapVersion.SOAP12;
import static com.predic8.membrane.core.http.MimeType.TEXT_XML;
import static com.predic8.membrane.core.http.Response.ok;
import static com.predic8.membrane.core.util.RecordingServerTestUtil.freePort;
import static com.predic8.membrane.core.util.RecordingServerTestUtil.startRecordingServer;
import static com.predic8.membrane.core.util.SOAPUtil.analyseSOAPMessage;
import static org.junit.jupiter.api.Assertions.*;


public class SOAPUtilTest {

    private final static String TB_NS = "http://thomas-bayer.com/blz/";
    private final static String MEMBRANE_NS = "http://membrane-api.io/";

    @Test
    void faultCheckSpecExample() throws Exception {
        assertTrue(SOAPUtil.analyseSOAPMessage(new XOPReconstitutor(), getMessage("src/test/resources/wsdlValidator/soapFaultFromSpec.xml")).isFault());
    }

    @Test
    void faultCustom() throws Exception {
        assertTrue(SOAPUtil.analyseSOAPMessage(new XOPReconstitutor(), getMessage("src/test/resources/wsdlValidator/soapFaultCustom.xml")).isFault());
    }

    @Test
    void analyseXML() {
        SOAPUtil.SOAPAnalysisResult result = analyseSOAPMessage(new XOPReconstitutor(), getMessageFromString("<foo/>"));
        assertFalse(result.isSOAP());
        assertFalse(result.isFault());
    }

    @Test
    void analyseSOAP11() {
        SOAPUtil.SOAPAnalysisResult result = analyseSOAPMessage(new XOPReconstitutor(), getMessageFromString("""
                <s11:Envelope xmlns:s11= "http://schemas.xmlsoap.org/soap/envelope/" >
                  <s11:Body>
                	<ns1:getBank xmlns:ns1="http://thomas-bayer.com/blz/">
                	  <ns1:blz>66762332</ns1:blz>
                	</ns1:getBank>
                  </s11:Body>
                </s11:Envelope>
                """));
        assertTrue(result.isSOAP());
        assertFalse(result.isFault());
        assertEquals(SOAP11, result.version());
        assertEquals(new QName(TB_NS, "getBank"), result.soapElement());
    }

    @Test
    void analyseSOAP12() {
        SOAPUtil.SOAPAnalysisResult result = analyseSOAPMessage(new XOPReconstitutor(), getMessageFromString("""
                <s12:Envelope xmlns:s12="http://www.w3.org/2003/05/soap-envelope">
                   <s12:Body>
                	  <Bar xmlns="http://membrane-api.io/"/>
                   </s12:Body>
                </s12:Envelope>
                """));
        assertTrue(result.isSOAP());
        assertFalse(result.isFault());
        assertEquals(SOAP12, result.version());
        assertEquals(new QName(MEMBRANE_NS, "Bar"), result.soapElement());
    }

    /**
     * The header-skipping test is on the local name alone, so a payload element that happens to be
     * named <code>Header</code> must not be swallowed by it - that would report a non-empty body as
     * empty, and WSDLValidator rejects a request whose soapElement is null.
     */
    @Test
    void analyseBodyElementNamedHeader() {
        SOAPUtil.SOAPAnalysisResult result = analyseSOAPMessage(new XOPReconstitutor(), getMessageFromString("""
                <s11:Envelope xmlns:s11="http://schemas.xmlsoap.org/soap/envelope/">
                  <s11:Body>
                    <ns1:Header xmlns:ns1="http://membrane-api.io/">
                      <ns1:value>42</ns1:value>
                    </ns1:Header>
                  </s11:Body>
                </s11:Envelope>
                """));
        assertTrue(result.isSOAP());
        assertFalse(result.isFault());
        assertEquals(SOAP11, result.version());
        assertEquals(new QName(MEMBRANE_NS, "Header"), result.soapElement());
    }

    /**
     * The case the skip must still handle: a real soap:Header ahead of a non-empty Body.
     */
    @Test
    void analyseSOAP11WithHeaderAndNonEmptyBody() {
        SOAPUtil.SOAPAnalysisResult result = analyseSOAPMessage(new XOPReconstitutor(), getMessageFromString("""
                <s11:Envelope xmlns:s11="http://schemas.xmlsoap.org/soap/envelope/">
                  <s11:Header>
                    <ns1:tracking xmlns:ns1="http://membrane-api.io/">abc</ns1:tracking>
                  </s11:Header>
                  <s11:Body>
                    <ns1:getBank xmlns:ns1="http://thomas-bayer.com/blz/"/>
                  </s11:Body>
                </s11:Envelope>
                """));
        assertTrue(result.isSOAP());
        assertFalse(result.isFault());
        assertEquals(SOAP11, result.version());
        assertEquals(new QName(TB_NS, "getBank"), result.soapElement());
    }

    /**
     * A header-only envelope is not a usable SOAP message: there is no Body, so nothing reports a
     * payload element.
     */
    @Test
    void analyseEnvelopeWithHeaderButNoBody() {
        SOAPUtil.SOAPAnalysisResult result = analyseSOAPMessage(new XOPReconstitutor(), getMessageFromString("""
                <s11:Envelope xmlns:s11="http://schemas.xmlsoap.org/soap/envelope/">
                  <s11:Header/>
                </s11:Envelope>
                """));
        assertFalse(result.isSOAP());
    }

    @Test
    void analyseSOAP12EmptyBody() {
        SOAPUtil.SOAPAnalysisResult result = analyseSOAPMessage(new XOPReconstitutor(), getMessageFromString("""
                <soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope">
                  <soap:Header></soap:Header>
                  <soap:Body></soap:Body>
                </soap:Envelope>
                """));
        assertTrue(result.isSOAP());
        assertFalse(result.isFault());
        assertEquals(SOAP12, result.version());
        // Null soapElement is what WSDLValidator uses to reject an empty body: keep it pinned.
        assertNull(result.soapElement());
    }

    @Test
    void analyseSOAP11EmptyBody() {
        SOAPUtil.SOAPAnalysisResult result = analyseSOAPMessage(new XOPReconstitutor(), getMessageFromString("""
                <s11:Envelope xmlns:s11="http://schemas.xmlsoap.org/soap/envelope/">
                  <s11:Body></s11:Body>
                </s11:Envelope>
                """));
        assertTrue(result.isSOAP());
        assertFalse(result.isFault());
        assertEquals(SOAP11, result.version());
        assertNull(result.soapElement());
    }

    @Test
    void analyseEnvelopeWithoutBody() {
        SOAPUtil.SOAPAnalysisResult result = analyseSOAPMessage(new XOPReconstitutor(), getMessageFromString("""
                <s11:Envelope xmlns:s11="http://schemas.xmlsoap.org/soap/envelope/">
                </s11:Envelope>
                """));
        assertFalse(result.isSOAP());
    }

    @Test
    void analyseFault11() {
        SOAPUtil.SOAPAnalysisResult result = analyseSOAPMessage(new XOPReconstitutor(), getMessageFromString("""
                <s11:Envelope xmlns:s11= "http://schemas.xmlsoap.org/soap/envelope/" >
                  <s11:Body>
                	<s11:Fault/>
                  </s11:Body>
                </s11:Envelope>
                """));
        assertTrue(result.isSOAP());
        assertTrue(result.isFault());
        assertEquals(SOAP11, result.version());
    }

    /**
     * Fault is not namespace prefixed. => Ok
     */
    @Test
    void analyseFault11DifferentNamespace() {
        SOAPUtil.SOAPAnalysisResult result = analyseSOAPMessage(new XOPReconstitutor(), getMessageFromString("""
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                  <soap:Body>
                    <Fault>
                      <faultcode>Client</faultcode>
                      <faultstring>WSDL message validation failed</faultstring>
                      <detail>
                        <error>Not a valid SOAP message.</error>
                      </detail>
                    </Fault>
                  </soap:Body>
                </soap:Envelope>
                """));
        assertTrue(result.isSOAP());
        assertTrue(result.isFault());
        assertEquals(SOAP11, result.version());
    }

    @Test
    void analyseSOAPMessageDoesNotFetchExternalDtd() throws Exception {
        var received = new AtomicBoolean(false);
        int port = freePort();
        TestRouter router = startRecordingServer(port, received);
        try {
            String malicious = """
                    <?xml version='1.0'?>
                    <!DOCTYPE s SYSTEM 'http://127.0.0.1:%d/x.dtd'>
                    <s11:Envelope xmlns:s11='http://schemas.xmlsoap.org/soap/envelope/'>
                      <s11:Body/>
                    </s11:Envelope>
                    """.formatted(port);
            analyseSOAPMessage(new XOPReconstitutor(), getMessageFromString(malicious));
        } finally {
            router.stop();
        }
        assertFalse(received.get(), "SOAPUtil.analyseSOAPMessage must not fetch external DTD");
    }


    private Message getMessageFromString(String body) {
        return ok().contentType(TEXT_XML).body(body).build();
    }

    private Message getMessage(String path) throws Exception {
        return ok().contentType(TEXT_XML).body(new FileInputStream(path), true).build();
    }
}
