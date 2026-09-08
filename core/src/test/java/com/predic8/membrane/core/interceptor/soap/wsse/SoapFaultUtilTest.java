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
package com.predic8.membrane.core.interceptor.soap.wsse;

import com.predic8.membrane.core.http.Response;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;

import static com.predic8.membrane.annot.Constants.SOAP11_NS;
import static com.predic8.membrane.annot.Constants.SOAP12_NS;
import static com.predic8.membrane.core.interceptor.soap.wsse.SoapFaultUtil.MEMBRANE_FAULT_NS;
import static com.predic8.membrane.core.interceptor.soap.wsse.WsSecurityFaultCode.FAILED_AUTHENTICATION;
import static com.predic8.membrane.core.interceptor.soap.wsse.WsSecurityXmlUtil.WSSE_NS;
import static org.junit.jupiter.api.Assertions.*;

class SoapFaultUtilTest {

    private static Document parse(Response response) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(response.getBody().getContent()));
    }

    private static Element only(Document doc, String namespace, String localName) {
        assertEquals(1, doc.getElementsByTagNameNS(namespace, localName).getLength(),
                "Expected exactly one " + localName);
        return (Element) doc.getElementsByTagNameNS(namespace, localName).item(0);
    }

    @Test
    void soap11FaultUsesUnqualifiedFaultcodeAndStatus500() throws Exception {
        Response response = SoapFaultUtil.create(SOAP11_NS, FAILED_AUTHENTICATION, "Password does not match.", false);

        // SOAP 1.1 requires HTTP 500 for every fault, regardless of who is at fault.
        assertEquals(500, response.getStatusCode());
        assertTrue(response.getHeader().getContentType().startsWith("text/xml"), response.getHeader().getContentType());

        Document doc = parse(response);
        assertEquals(SOAP11_NS, doc.getDocumentElement().getNamespaceURI());
        Element faultCode = only(doc, null, "faultcode");
        assertEquals("wsse:FailedAuthentication", faultCode.getTextContent());
        // The prefix used in the QName value has to be declared where it is used.
        assertEquals(WSSE_NS, faultCode.lookupNamespaceURI("wsse"));
        assertEquals(FAILED_AUTHENTICATION.getFaultString(), only(doc, null, "faultstring").getTextContent());
        assertEquals("Password does not match.", only(doc, MEMBRANE_FAULT_NS, "reason").getTextContent());
    }

    @Test
    void soap12FaultUsesSenderCodeWithWsseSubcodeAndStatus400() throws Exception {
        Response response = SoapFaultUtil.create(SOAP12_NS, FAILED_AUTHENTICATION, "Password does not match.", false);

        // SOAP 1.2's HTTP binding requires 400 for an env:Sender fault, which every WS-Security
        // fault is.
        assertEquals(400, response.getStatusCode());
        assertTrue(response.getHeader().getContentType().startsWith("application/soap+xml"),
                response.getHeader().getContentType());

        Document doc = parse(response);
        assertEquals(SOAP12_NS, doc.getDocumentElement().getNamespaceURI());
        // Two soap:Value elements: the Code's own, then the Subcode's.
        assertEquals(2, doc.getElementsByTagNameNS(SOAP12_NS, "Value").getLength());
        assertEquals("soap:Sender", doc.getElementsByTagNameNS(SOAP12_NS, "Value").item(0).getTextContent());
        Element subcodeValue = (Element) doc.getElementsByTagNameNS(SOAP12_NS, "Value").item(1);
        assertEquals("wsse:FailedAuthentication", subcodeValue.getTextContent());
        assertEquals(1, doc.getElementsByTagNameNS(SOAP12_NS, "Subcode").getLength());
        assertEquals(WSSE_NS, subcodeValue.lookupNamespaceURI("wsse"));
        assertEquals(FAILED_AUTHENTICATION.getFaultString(), only(doc, SOAP12_NS, "Text").getTextContent());
        assertEquals("Password does not match.", only(doc, MEMBRANE_FAULT_NS, "reason").getTextContent());
    }

    /**
     * The reason is what makes a fault an oracle, so production mode replaces it with a log key. The
     * fault code and string stay: they are what a conforming SOAP client dispatches on, and the
     * spec-mandated fault string reveals nothing beyond "the header was rejected".
     */
    @Test
    void productionModeReplacesTheReasonWithALogKey() throws Exception {
        Response response = SoapFaultUtil.create(SOAP11_NS, FAILED_AUTHENTICATION, "Password does not match.", true);

        Document doc = parse(response);
        assertEquals("wsse:FailedAuthentication", only(doc, null, "faultcode").getTextContent());
        assertEquals(FAILED_AUTHENTICATION.getFaultString(), only(doc, null, "faultstring").getTextContent());

        String reason = only(doc, MEMBRANE_FAULT_NS, "reason").getTextContent();
        assertFalse(reason.contains("Password does not match."), reason);
        assertTrue(reason.startsWith("Details are hidden. See server log (key: "), reason);
    }

    @Test
    void noDetailElementWhenThereIsNoReason() throws Exception {
        Document doc = parse(SoapFaultUtil.create(SOAP11_NS, FAILED_AUTHENTICATION, null, false));

        assertEquals(0, doc.getElementsByTagNameNS(null, "detail").getLength());
    }
}
