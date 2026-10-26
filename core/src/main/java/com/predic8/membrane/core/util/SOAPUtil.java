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
import org.xml.sax.SAXParseException;

import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.EntityReference;
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
        return readEnvelope(xopr, msg, false).analysis();
    }

    /**
     * Analyses the message like {@link #analyseSOAPMessage}, but reads it to the end, so that it is
     * checked as a whole: whether it is well-formed, and whether its envelope has the structure
     * SOAP 1.1 resp. 1.2 requires - at most one {@code Header}, in front of exactly one
     * {@code Body}, both in the namespace of the {@code Envelope}, and, in SOAP 1.2, no element
     * after the {@code Body}. SOAP 1.1 allows further elements there.
     */
    public static SOAPEnvelopeValidation validateSOAPEnvelope(XOPReconstitutor xopr, Message msg) {
        return readEnvelope(xopr, msg, true);
    }

    private static SOAPEnvelopeValidation readEnvelope(XOPReconstitutor xopr, Message msg, boolean wholeMessage) {
        log.debug("Analyzing SOAP message: {}", msg);
        final var reader = new EnvelopeReader(wholeMessage);
        try {
            final var parser = HardenedStaxInputFactory.inputFactory().createXMLEventReader(xopr.reconstituteIfNecessary(msg));
            while (parser.hasNext()) {
                if (!reader.read(parser.nextEvent()))
                    break;
            }
        } catch (ReadingBodyException e) {
            // As in isSOAP: a body that could not be read says nothing about whether it was SOAP.
            // Reporting it as "not SOAP" would turn a transport failure into a content verdict.
            throw e;
        } catch (Exception e) {
            if (reader.analysis == null) {
                log.info("Error parsing SOAP message: {}", e.getMessage());
                log.debug("Ignoring exception: ", e);
                return new SOAPEnvelopeValidation(NO_SOAP_RESULT, List.of());
            }
            reader.errors.add(e instanceof XMLStreamException xse ? toSAXParseException(xse) : e);
        }
        if (reader.analysis == null) {
            log.info("No SOAP Element found.");
            return new SOAPEnvelopeValidation(NO_SOAP_RESULT, List.of());
        }
        return new SOAPEnvelopeValidation(reader.analysis, reader.analysis.isSOAP() ? reader.errors : List.of());
    }

    /**
     * The JDK's StAX parser puts the location in front of the message of a parse error, Woodstox
     * after it.
     */
    private static final String LOCATION_IN_FRONT = "Message: ";
    private static final String LOCATION_AFTER = "\n at [row,col";

    /**
     * Reports a parse error with its location in fields of its own, as the SAX parser does,
     * instead of in the message.
     */
    private static SAXParseException toSAXParseException(XMLStreamException e) {
        final var location = e.getLocation();
        var message = e.getMessage();
        final var start = message.indexOf(LOCATION_IN_FRONT);
        if (start >= 0)
            message = message.substring(start + LOCATION_IN_FRONT.length());
        final var end = message.indexOf(LOCATION_AFTER);
        if (end >= 0)
            message = message.substring(0, end);
        return location == null
                ? new SAXParseException(message, null)
                : new SAXParseException(message, null, null, location.getLineNumber(), location.getColumnNumber());
    }

    /**
     * Reads the events of a message, one at a time, and keeps track of where they are in the
     * envelope.
     */
    private static final class EnvelopeReader {

        private static final int ENVELOPE_DEPTH = 1;
        private static final int ENVELOPE_CHILD_DEPTH = 2;
        private static final int BODY_ENTRY_DEPTH = 3;

        /**
         * Where the reader is among the children of the envelope.
         */
        private enum Position {
            START,
            /**
             * A header has started, the body has not.
             */
            AFTER_HEADER,
            IN_BODY,
            AFTER_BODY
        }

        /**
         * Whether to read on once the message is recognized as SOAP, to check the rest of it.
         */
        private final boolean wholeMessage;

        private final List<Exception> errors = new ArrayList<>();

        /**
         * Null until the message is recognized as SOAP or not.
         */
        private SOAPAnalysisResult analysis;

        private QName envelope;

        private Position position = Position.START;

        /**
         * Depth of the current element: {@code 1} for the envelope, {@code 2} for its children.
         */
        private int depth;

        EnvelopeReader(boolean wholeMessage) {
            this.wholeMessage = wholeMessage;
        }

        /**
         * @return {@code false} if the rest of the message is not needed
         */
        boolean read(XMLEvent event) {
            if (event.isStartElement())
                return startElement(event.asStartElement());
            if (event.isEndElement())
                return endElement();
            // Without a DTD no entity is declared. Some parsers report the reference as an event
            // instead of as an error.
            if (event.isEntityReference())
                error(event, "The entity \"%s\" was referenced, but not declared.".formatted(((EntityReference) event).getName()));
            // A Body holds elements only (SOAP 1.1 4.3, SOAP 1.2 5.3). Text in it would be taken
            // for an empty Body if it is not followed by an element.
            if (position == Position.IN_BODY && analysis == null && event.isCharacters() && !event.asCharacters().isWhiteSpace())
                return notSOAP("Text in SOAP Body.", null);
            return true;
        }

        private boolean startElement(StartElement element) {
            final var name = element.getName();
            depth++;
            if (depth == ENVELOPE_DEPTH) {
                if (!"Envelope".equals(name.getLocalPart()) || !isSOAPNamespace(name.getNamespaceURI()))
                    return notSOAP("Not a SOAP Envelope: {}", name);
                envelope = name;
                return true;
            }
            if (depth == ENVELOPE_CHILD_DEPTH)
                return envelopeChild(element);
            if (depth == BODY_ENTRY_DEPTH && position == Position.IN_BODY && analysis == null) {
                final var fault = "Fault".equals(name.getLocalPart());
                return recognized(new SOAPAnalysisResult(true, fault, getSOAPVersion(envelope), fault ? null : name));
            }
            return true;
        }

        private boolean envelopeChild(StartElement element) {
            final var name = element.getName();
            final var soap = isSOAPNamespace(name.getNamespaceURI());
            if (position == Position.AFTER_BODY) {
                if (soap && "Header".equals(name.getLocalPart()))
                    error(element, "The SOAP Header must come before the Body.");
                else if (soap && "Body".equals(name.getLocalPart()))
                    error(element, "The SOAP Envelope must not contain more than one Body.");
                else if (getSOAPVersion(envelope) == SoapVersion.SOAP12)
                    error(element, "A SOAP 1.2 Envelope must not contain an element after the Body.");
                return true;
            }
            if (!soap)
                return notSOAP("Not a SOAP Element: {}", name);
            switch (name.getLocalPart()) {
                case "Header" -> {
                    if (position == Position.AFTER_HEADER)
                        error(element, "The SOAP Envelope must not contain more than one Header.");
                    position = Position.AFTER_HEADER;
                }
                case "Body" -> position = Position.IN_BODY;
                default -> {
                    return notSOAP("Not a SOAP Element: {}", name);
                }
            }
            if (!envelope.getNamespaceURI().equals(name.getNamespaceURI()))
                error(element, "The SOAP %s must be in the namespace of the Envelope, %s.".formatted(name.getLocalPart(), envelope.getNamespaceURI()));
            return true;
        }

        private boolean endElement() {
            final var closing = depth--;
            if (closing == ENVELOPE_CHILD_DEPTH && position == Position.IN_BODY) {
                position = Position.AFTER_BODY;
                // The Body held no element at all, which is reported with a null soapElement.
                if (analysis == null)
                    return recognized(new SOAPAnalysisResult(true, false, getSOAPVersion(envelope), null));
            }
            // Closed before a Body ever opened, e.g. an envelope carrying only a header.
            if (closing == ENVELOPE_DEPTH && analysis == null)
                return notSOAP("SOAP Envelope without Body.", null);
            return true;
        }

        private boolean recognized(SOAPAnalysisResult result) {
            analysis = result;
            return wholeMessage;
        }

        private boolean notSOAP(String message, QName name) {
            log.debug(message, name);
            analysis = NO_SOAP_RESULT;
            return false;
        }

        private void error(XMLEvent event, String message) {
            final var location = event.getLocation();
            errors.add(new SAXParseException(message, null, null, location.getLineNumber(), location.getColumnNumber()));
        }
    }

    private static SoapVersion getSOAPVersion(QName name) {
        return switch (name.getNamespaceURI()) {
            case SOAP11_NS -> SoapVersion.SOAP11;
            case SOAP12_NS -> SoapVersion.SOAP12;
            default -> SoapVersion.UNKNOWN;
        };
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
