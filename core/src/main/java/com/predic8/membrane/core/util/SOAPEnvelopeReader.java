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

import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.http.ReadingBodyException;
import com.predic8.membrane.core.multipart.XOPReconstitutor;
import com.predic8.membrane.core.util.SOAPUtil.SOAPAnalysisResult;
import com.predic8.membrane.core.util.SOAPUtil.SOAPEnvelopeValidation;
import com.predic8.membrane.core.util.xml.parser.HardenedStaxInputFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXParseException;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.EntityReference;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import java.util.ArrayList;
import java.util.List;

import static com.predic8.membrane.annot.Constants.*;
import static com.predic8.membrane.core.util.SOAPUtil.NO_SOAP_RESULT;
import static com.predic8.membrane.core.util.SOAPUtil.isSOAPNamespace;

/**
 * Reads a SOAP message event by event and keeps track of where the events are in the envelope,
 * for {@link SOAPUtil#analyseSOAPMessage} and {@link SOAPUtil#validateSOAPEnvelope}. One instance
 * reads one message.
 */
final class SOAPEnvelopeReader {

    private static final Logger log = LoggerFactory.getLogger(SOAPEnvelopeReader.class.getName());

    private static final int ENVELOPE_DEPTH = 1;
    private static final int ENVELOPE_CHILD_DEPTH = 2;
    private static final int BODY_ENTRY_DEPTH = 3;

    /**
     * The JDK's StAX parser puts the location in front of the message of a parse error, Woodstox
     * after it.
     */
    private static final String LOCATION_IN_FRONT = "Message: ";
    private static final String LOCATION_AFTER = "\n at [row,col";

    /**
     * Where the reader is among the children of the envelope.
     */
    private enum Position {
        START,
        IN_HEADER,
        /**
         * A header has ended, the body has not started.
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
     * The first entry of the body.
     */
    private QName firstBodyEntry;

    /**
     * Whether a Fault was found next to another body entry.
     */
    private boolean faultNotAlone;

    /**
     * Depth of the current element: {@code 1} for the envelope, {@code 2} for its children.
     */
    private int depth;

    private SOAPEnvelopeReader(boolean wholeMessage) {
        this.wholeMessage = wholeMessage;
    }

    /**
     * @param wholeMessage {@code false} to stop at the start of the body element, once the message
     *                     is recognized, {@code true} to read on and check the rest of the message
     */
    static SOAPEnvelopeValidation read(XOPReconstitutor xopr, Message msg, boolean wholeMessage) {
        log.debug("Analyzing SOAP message: {}", msg);
        final var reader = new SOAPEnvelopeReader(wholeMessage);
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
     * @return {@code false} if the rest of the message is not needed
     */
    private boolean read(XMLEvent event) {
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
        if (depth == BODY_ENTRY_DEPTH && position == Position.IN_BODY)
            return bodyEntry(element);
        // SOAP 1.1 4.2, SOAP 1.2 Part 1 5.2.1
        if (depth == BODY_ENTRY_DEPTH && position == Position.IN_HEADER && name.getNamespaceURI().isEmpty())
            error(element, "A SOAP header block must be namespace-qualified.");
        return true;
    }

    /**
     * The first entry decides whether the message is a fault. Only a Fault that is the only entry
     * makes a fault message, though (SOAP 1.2 Part 1 5.4): a fault message is not validated like
     * other messages, so further entries would go unvalidated.
     */
    private boolean bodyEntry(StartElement element) {
        final var name = element.getName();
        final var fault = isFault(name);
        if (analysis == null) {
            firstBodyEntry = name;
            return recognized(new SOAPAnalysisResult(true, fault, getSOAPVersion(envelope), fault ? null : name));
        }
        if (!faultNotAlone && (fault || analysis.isFault())) {
            faultNotAlone = true;
            error(element, "A SOAP Body with a Fault must not contain any other element.");
            analysis = new SOAPAnalysisResult(true, false, analysis.version(), firstBodyEntry);
        }
        return true;
    }

    /**
     * A Fault in the namespace of the Envelope. An unqualified one is taken for a Fault as well, as
     * some implementations send it that way.
     */
    private boolean isFault(QName name) {
        return "Fault".equals(name.getLocalPart())
                && (envelope.getNamespaceURI().equals(name.getNamespaceURI()) || name.getNamespaceURI().isEmpty());
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
                position = Position.IN_HEADER;
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
        if (closing == ENVELOPE_CHILD_DEPTH && position == Position.IN_HEADER)
            position = Position.AFTER_HEADER;
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

    private static SoapVersion getSOAPVersion(QName name) {
        return switch (name.getNamespaceURI()) {
            case SOAP11_NS -> SoapVersion.SOAP11;
            case SOAP12_NS -> SoapVersion.SOAP12;
            default -> SoapVersion.UNKNOWN;
        };
    }
}
