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
import com.predic8.membrane.core.util.xml.XMLUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

import static com.predic8.membrane.annot.Constants.SOAP11_NS;
import static com.predic8.membrane.annot.Constants.SOAP12_NS;
import static com.predic8.membrane.core.exceptions.ProblemDetails.LOG_KEY;
import static com.predic8.membrane.core.http.MimeType.APPLICATION_SOAP_XML;
import static com.predic8.membrane.core.http.MimeType.TEXT_XML;
import static com.predic8.membrane.core.interceptor.soap.wsse.WsSecurityXmlUtil.WSSE_NS;
import static java.util.UUID.randomUUID;

/**
 * Renders a WS-Security failure as a {@code soap:Fault} rather than as Problem Details (see
 * ADR-006): a SOAP client cannot consume RFC 7807, and WS-Security prescribes the fault codes to
 * use. The fault mirrors the envelope version of the offending message, since a SOAP 1.1 client
 * cannot parse a SOAP 1.2 fault and vice versa.
 */
final class SoapFaultUtil {

    private static final Logger log = LoggerFactory.getLogger(SoapFaultUtil.class);

    static final String MEMBRANE_FAULT_NS = "https://membrane-api.io/soap-fault";

    private SoapFaultUtil() {
    }

    /**
     * @param soapNs     the envelope namespace of the message that failed, which decides both the
     *                   fault's shape and the response's status code and content type
     * @param detail     the concrete reason, only rendered when not in production mode
     * @param production whether to suppress {@code detail} and log it under a key instead, matching
     *                   {@link com.predic8.membrane.core.exceptions.ProblemDetails}' behaviour
     */
    static Response create(String soapNs, WsSecurityFaultCode code, String detail, boolean production) {
        boolean soap12 = SOAP12_NS.equals(soapNs);
        String reason = renderableDetail(code, detail, production);
        Element envelope = soap12 ? soap12Fault(code, reason) : soap11Fault(code, reason);
        try {
            return Response.statusCode(soap12 ? 400 : 500)
                    // SOAP 1.1 requires HTTP 500 for any fault; SOAP 1.2's HTTP binding requires 400
                    // for an env:Sender fault, which every WS-Security fault is.
                    .contentType((soap12 ? APPLICATION_SOAP_XML : TEXT_XML) + ";charset=UTF-8")
                    .body(XMLUtil.xmlNode2String(envelope))
                    .build();
        } catch (TransformerException e) {
            throw new IllegalStateException("Could not serialize the SOAP fault.", e);
        }
    }

    /**
     * Production mode must not leak why a check failed, so the reason is logged under a key that the
     * client is given instead - enough for an operator to correlate a report with the log, and
     * nothing more.
     */
    private static String renderableDetail(WsSecurityFaultCode code, String detail, boolean production) {
        if (detail == null) {
            return null;
        }
        if (!production) {
            return detail;
        }
        String logKey = randomUUID().toString();
        try {
            MDC.put(LOG_KEY, logKey);
            log.info("WS-Security fault detail hidden. code=wsse:{}, detail={}", code.getLocalName(), detail);
        } finally {
            MDC.remove(LOG_KEY);
        }
        return "Details are hidden. See server log (key: %s)".formatted(logKey);
    }

    private static Element soap11Fault(WsSecurityFaultCode code, String detail) {
        Document doc = newDocument();
        Element fault = envelopeWithFault(doc, SOAP11_NS);

        // faultcode/faultstring/detail are unqualified in SOAP 1.1, and the faultcode's QName value
        // needs the wsse prefix to be in scope where it is used.
        Element faultCode = doc.createElementNS(null, "faultcode");
        faultCode.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:wsse", WSSE_NS);
        faultCode.setTextContent("wsse:" + code.getLocalName());
        fault.appendChild(faultCode);

        fault.appendChild(textElement(doc, null, "faultstring", code.getFaultString()));

        if (detail != null) {
            Element detailElement = doc.createElementNS(null, "detail");
            detailElement.appendChild(textElement(doc, MEMBRANE_FAULT_NS, "m:reason", detail));
            fault.appendChild(detailElement);
        }
        return doc.getDocumentElement();
    }

    private static Element soap12Fault(WsSecurityFaultCode code, String detail) {
        Document doc = newDocument();
        Element fault = envelopeWithFault(doc, SOAP12_NS);

        Element codeElement = doc.createElementNS(SOAP12_NS, "soap:Code");
        codeElement.appendChild(textElement(doc, SOAP12_NS, "soap:Value", "soap:Sender"));
        Element subcode = doc.createElementNS(SOAP12_NS, "soap:Subcode");
        Element subcodeValue = textElement(doc, SOAP12_NS, "soap:Value", "wsse:" + code.getLocalName());
        subcodeValue.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:wsse", WSSE_NS);
        subcode.appendChild(subcodeValue);
        codeElement.appendChild(subcode);
        fault.appendChild(codeElement);

        Element reason = doc.createElementNS(SOAP12_NS, "soap:Reason");
        Element text = textElement(doc, SOAP12_NS, "soap:Text", code.getFaultString());
        text.setAttributeNS(XMLConstants.XML_NS_URI, "xml:lang", "en");
        reason.appendChild(text);
        fault.appendChild(reason);

        if (detail != null) {
            Element detailElement = doc.createElementNS(SOAP12_NS, "soap:Detail");
            detailElement.appendChild(textElement(doc, MEMBRANE_FAULT_NS, "m:reason", detail));
            fault.appendChild(detailElement);
        }
        return doc.getDocumentElement();
    }

    private static Element envelopeWithFault(Document doc, String soapNs) {
        Element envelope = doc.createElementNS(soapNs, "soap:Envelope");
        doc.appendChild(envelope);
        Element body = doc.createElementNS(soapNs, "soap:Body");
        envelope.appendChild(body);
        Element fault = doc.createElementNS(soapNs, "soap:Fault");
        body.appendChild(fault);
        return fault;
    }

    private static Element textElement(Document doc, String namespace, String qualifiedName, String text) {
        Element element = doc.createElementNS(namespace, qualifiedName);
        element.setTextContent(text);
        return element;
    }

    private static Document newDocument() {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            return factory.newDocumentBuilder().newDocument();
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException("Could not create a DOM document for the SOAP fault.", e);
        }
    }
}
