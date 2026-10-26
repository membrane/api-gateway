/* Copyright 2012 predic8 GmbH, www.predic8.com

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

import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.http.ReadingBodyException;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.schemavalidation.FaultDetailXMLFilter;
import com.predic8.membrane.core.interceptor.schemavalidation.SOAPXMLFilter;
import com.predic8.membrane.core.multipart.XOPReconstitutor;
import com.predic8.membrane.core.util.xml.parser.HardenedSaxParser;
import com.predic8.membrane.core.util.xml.parser.HardenedStaxInputFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import javax.xml.transform.Source;
import javax.xml.transform.sax.SAXSource;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.predic8.membrane.annot.Constants.*;
import static com.predic8.membrane.core.http.MimeType.TEXT_XML_UTF8;
import static com.predic8.membrane.core.http.Response.ok;
import static com.predic8.membrane.core.util.xml.XMLUtil.mapToXml;
import static com.predic8.membrane.core.util.xml.XMLUtil.xmlNode2String;

public class SOAPUtil {

    private static final Logger log = LoggerFactory.getLogger(SOAPUtil.class.getName());
    public static final SOAPAnalysisResult NO_SOAP_RESULT = new SOAPAnalysisResult(false, false, null, null);

    public static boolean isSOAP( XOPReconstitutor xopr, Message msg) {
        try {
            XMLEventReader parser = HardenedStaxInputFactory.inputFactory().createXMLEventReader(xopr.reconstituteIfNecessary(msg));

            while (parser.hasNext()) {
                XMLEvent event = parser.nextEvent();
                if (event.isStartElement()) {
                    QName name = ((StartElement) event).getName();
                    return (isSOAP11Element(name)
                            || isSOAP12Element(name)) &&
                            "Envelope".equals(name.getLocalPart());
                }
            }
        } catch (ReadingBodyException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Ignoring exception: ", e);
        }
        return false;
    }

    public enum FaultCode {Server, Client}

    public static Response createSOAPFaultResponse(FaultCode code, String faultstring, Map<String,Object> details, SoapVersion version) {
        try {
            Element fault = version == SoapVersion.SOAP12
                    ? createSOAP12Fault(code, faultstring, details)
                    : createSOAP11Fault(code, faultstring, details);
            return ok().contentType(TEXT_XML_UTF8).body(xmlNode2String(fault)).build();
        } catch (Exception e) {
            throw new RuntimeException("Should not happen", e);
        }
    }

    public static Element createSOAP11Fault(FaultCode faultcode, String faultstring, Map<String, Object> detail) throws ParserConfigurationException {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();

        Element env = doc.createElementNS(SOAP11_NS, "soap:Envelope");
        env.setAttribute("xmlns:soap", SOAP11_NS);
        doc.appendChild(env);

        Element body = doc.createElementNS(SOAP11_NS, "soap:Body");
        env.appendChild(body);

        Element fault = doc.createElementNS(SOAP11_NS, "soap:Fault");
        body.appendChild(fault);

        Element faultCode = doc.createElement("faultcode");
        faultCode.setTextContent("soap:" + faultcode.name());
        fault.appendChild(faultCode);

        Element faultString = doc.createElement("faultstring");
        faultString.setTextContent(faultstring);
        fault.appendChild(faultString);

        if (detail != null && !detail.isEmpty()) {
            Element detailElement = doc.createElement("detail");
            mapToXml(doc, detailElement, detail);
            fault.appendChild(detailElement);
        }

        return env;
    }

    public static Element createSOAP12Fault(FaultCode faultcode, String faultstring, Map<String, Object> detail) throws ParserConfigurationException {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();

        Element env = doc.createElementNS(SOAP12_NS, "soap:Envelope");
        env.setAttribute("xmlns:soap", SOAP12_NS);
        doc.appendChild(env);

        Element body = doc.createElementNS(SOAP12_NS, "soap:Body");
        env.appendChild(body);

        Element fault = doc.createElementNS(SOAP12_NS, "soap:Fault");
        body.appendChild(fault);

        Element code = doc.createElementNS(SOAP12_NS, "soap:Code");
        fault.appendChild(code);

        Element value = doc.createElementNS(SOAP12_NS, "soap:Value");
        // faultcode.name() is Client/Server; SOAP 1.2's fault-code vocabulary is Sender/Receiver.
        value.setTextContent("soap:" + (faultcode == FaultCode.Client ? "Sender" : "Receiver"));
        code.appendChild(value);

        Element reason = doc.createElementNS(SOAP12_NS, "soap:Reason");
        fault.appendChild(reason);

        Element text = doc.createElementNS(SOAP12_NS, "soap:Text");
        text.setAttributeNS(XMLConstants.XML_NS_URI, "xml:lang", "en");
        text.setTextContent(faultstring);
        reason.appendChild(text);

        if (detail != null && !detail.isEmpty()) {
            Element detailElement = doc.createElementNS(SOAP12_NS, "soap:Detail");
            mapToXml(doc, detailElement, detail);
            fault.appendChild(detailElement);
        }

        return env;
    }

    public record SOAPAnalysisResult(boolean isSOAP, boolean isFault, SoapVersion version, QName soapElement) {
    }

    /**
     * A SOAP message read as a whole.
     *
     * @param analysis what {@link #analyseSOAPMessage} reports for the message
     * @param errors   where the message is not well-formed or its envelope breaks the structure
     *                 SOAP requires, found after the message was recognized as SOAP; empty for a
     *                 message that is no SOAP message
     */
    public record SOAPEnvelopeValidation(SOAPAnalysisResult analysis, List<Exception> errors) {
        public SOAPEnvelopeValidation {
            errors = List.copyOf(errors);
        }
    }

    /**
     * Recognizes a SOAP message by its envelope and finds its body element. Reads the message only
     * up to the start of the body element.
     */
    public static SOAPAnalysisResult analyseSOAPMessage( XOPReconstitutor xopr, Message msg) {
        return SOAPEnvelopeReader.read(xopr, msg, false).analysis();
    }

    /**
     * Analyses the message like {@link #analyseSOAPMessage}, but reads it to the end, so that it is
     * checked as a whole: whether it is well-formed, and whether its envelope has the structure
     * SOAP 1.1 resp. 1.2 requires - at most one {@code Header}, in front of exactly one
     * {@code Body}, both in the namespace of the {@code Envelope}, and, in SOAP 1.2, no element
     * after the {@code Body}. SOAP 1.1 allows further elements there.
     */
    public static SOAPEnvelopeValidation validateSOAPEnvelope(XOPReconstitutor xopr, Message msg) {
        return SOAPEnvelopeReader.read(xopr, msg, true);
    }

    public static boolean isSOAP12Element(QName name) {
        return SOAP12_NS.equals(name.getNamespaceURI());
    }

    public static boolean isSOAP11Element(QName name) {
        return SOAP11_NS.equals(name.getNamespaceURI());
    }

    /**
     * Whether the namespace is the envelope namespace of SOAP 1.1 or SOAP 1.2.
     */
    public static boolean isSOAPNamespace(String uri) {
        return SOAP11_NS.equals(uri) || SOAP12_NS.equals(uri);
    }

    /**
     * Extracts the QNames of the detail entries - the direct children of a SOAP fault's
     * {@code detail} (SOAP 1.1, unqualified) or {@code Detail} (SOAP 1.2,
     * {@link com.predic8.membrane.annot.Constants#SOAP12_NS}) element. Descendants below the
     * entries themselves are not reported.
     *
     * @return the detail entries in document order; empty if the fault carries no detail, the
     *         detail element is empty, or the message could not be parsed.
     */
    public static List<QName> extractFaultDetailElements(XOPReconstitutor xopr, Message msg, SoapVersion version) {
        var entries = new ArrayList<QName>();
        try {
            var parser = HardenedStaxInputFactory.inputFactory().createXMLEventReader(xopr.reconstituteIfNecessary(msg));
            int depth = -1; // -1 outside detail, 0 on the detail element, n when n levels inside it
            // Only consulted while `depth` is still -1 (detail not found yet): -1 before/after
            // Fault, 0 at Fault's direct children (where detail is looked for), n>0 when nested
            // deeper inside one of Fault's other children. Scopes the match to a direct child of
            // Fault, so a same-named element elsewhere in the message (e.g. in soap:Header) isn't
            // mistaken for the fault's own detail.
            int faultDepth = -1;
            while (parser.hasNext()) {
                var event = parser.nextEvent();
                if (event.isStartElement()) {
                    var name = ((StartElement) event).getName();
                    if (depth < 0) {
                        if (faultDepth == 0 && isDetailElement(name, version)) {
                            depth = 0;
                            continue;
                        }
                        if (faultDepth < 0) {
                            if (isFaultElement(name))
                                faultDepth = 0;
                        } else {
                            faultDepth++;
                        }
                        continue;
                    }
                    if (++depth == 1)
                        entries.add(name);
                    continue;
                }
                if (event.isEndElement()) {
                    if (depth >= 0) {
                        if (depth == 0)
                            return entries; // detail closed
                        depth--;
                        continue;
                    }
                    var name = ((EndElement) event).getName();
                    if (faultDepth == 0 && isFaultElement(name)) {
                        faultDepth = -1; // left Fault without finding a detail element
                    } else if (faultDepth > 0) {
                        faultDepth--;
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Could not extract fault detail elements: {}", e.getMessage());
        }
        return entries;
    }

    private static boolean isDetailElement(QName name, SoapVersion version) {
        return switch (version) {
            case SOAP11 -> "detail".equals(name.getLocalPart()) && name.getNamespaceURI().isEmpty();
            case SOAP12 -> "Detail".equals(name.getLocalPart()) && SOAP12_NS.equals(name.getNamespaceURI());
            default -> false;
        };
    }

    private static boolean isFaultElement(QName name) {
        return "Fault".equals(name.getLocalPart())
                && (SOAP11_NS.equals(name.getNamespaceURI()) || SOAP12_NS.equals(name.getNamespaceURI()));
    }

    /**
     * Strips the SOAP envelope so that only the payload below {@code <soap:Body>} is passed on.
     */
    public static Source getSOAPBody(InputStream stream) {
        try {
            return new SAXSource(new SOAPXMLFilter(HardenedSaxParser.newSAXParser().getXMLReader()), new InputSource(stream));
        } catch (SAXException e) {
            throw new RuntimeException("Error initializing SAXSource", e);
        }
    }

    /**
     * Narrows a SOAP fault message down to the content of its {@code detail}/{@code Detail}
     * element, for schema-validating a fault's payload separately from its envelope structure.
     */
    public static Source getFaultDetailBody(InputStream stream, SoapVersion version) {
        try {
            return new SAXSource(new FaultDetailXMLFilter(HardenedSaxParser.newSAXParser().getXMLReader(), version), new InputSource(stream));
        } catch (SAXException e) {
            throw new RuntimeException("Error initializing SAXSource", e);
        }
    }
}
