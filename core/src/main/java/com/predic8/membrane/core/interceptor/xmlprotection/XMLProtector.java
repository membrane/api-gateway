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

package com.predic8.membrane.core.interceptor.xmlprotection;

import com.predic8.membrane.core.interceptor.xmlprotection.XMLProtectionResult.Rejected;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.namespace.QName;
import javax.xml.stream.*;
import javax.xml.stream.events.*;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.Charset;

import static com.predic8.membrane.core.interceptor.xmlprotection.DoctypeInspector.containsExternalEntityReferences;
import static com.predic8.membrane.core.interceptor.xmlprotection.DoctypeInspector.hasExternalSubsetReference;
import static com.predic8.membrane.core.interceptor.xmlprotection.XMLLimits.exceeds;
import static com.predic8.membrane.core.interceptor.xmlprotection.XMLProtectionResult.ACCEPTED;
import static com.predic8.membrane.core.interceptor.xmlprotection.XMLProtectionResult.REWRITTEN;

/**
 * Copies an XML document to a writer, filtering out what looks like an attack on the backend parser:
 * <ul>
 * <li>DTDs are removed, or make the document fail if removal is switched off.</li>
 * <li>A DOCTYPE that points outside the document - an external entity declaration or an external
 *     subset - makes the document fail; the reference itself is never followed.</li>
 * <li>Element and attribute name length, attribute count per element and nesting depth can be
 *     limited. Names count as the document spells them, {@code prefix:localName}.</li>
 * </ul>
 * <p>
 * A {@link Rejected} result means the copy stopped mid-document: the {@link Writer} is left at that
 * position, so its content has to be discarded and an error returned to the requestor.
 * Only a {@link XMLProtectionResult.Rewritten} result obliges the caller to forward what was written
 * instead of the document it received - nothing else here alters the document.
 * <p>
 * One instance scans one document, on one thread: it keeps that document's nesting depth as state,
 * and the StAX {@link XMLEventWriter} underneath is not safe to use from more than one thread.
 * Instances are therefore <b>not thread safe</b> - build one per message, on the thread that scans
 * it, as {@link XMLProtectionInterceptor} does. Holding one in a field of that interceptor would be
 * a race: a single interceptor instance serves every request thread.
 */
public class XMLProtector {
    private static final Logger log = LoggerFactory.getLogger(XMLProtector.class);

    /**
     * Writers are per document, the factory that makes them is not - and JAXP promises nothing about
     * sharing a factory between threads, so each thread keeps the one it built, as
     * {@link com.predic8.membrane.core.util.xml.XPathUtil} and
     * {@link com.predic8.membrane.core.util.xml.parser.HardenedStaxInputFactory} do.
     */
    private static final ThreadLocal<XMLOutputFactory> OUTPUT_FACTORY =
            ThreadLocal.withInitial(XMLOutputFactory::newInstance);
    private static final XMLEventFactory EVENT_FACTORY = XMLEventFactory.newFactory();

    private final XMLEventWriter writer;
    private final XMLInputFactory inputFactory;
    private final XMLLimits limits;
    private final Charset charset;

    /** Nesting depth of the element the reader is currently in. */
    private int depth;

    /**
     * @param inputFactory a DTD-aware, hardened factory, see
     *                     {@link com.predic8.membrane.core.util.xml.parser.HardenedStaxInputFactory#dtdAwareInputFactory()}
     * @param charset      the charset {@code out} actually encodes with, see
     *                     {@link #withActualEncoding(StartDocument)}
     */
    public XMLProtector(Writer out, XMLInputFactory inputFactory, XMLLimits limits, Charset charset) throws XMLStreamException {
        this.writer = OUTPUT_FACTORY.get().createXMLEventWriter(out);
        this.inputFactory = inputFactory;
        this.limits = limits;
        this.charset = charset;
    }

    /**
     * Copies the document to the writer, dropping the DTD if configured to, and stops at the first
     * violation.
     *
     * @param in reader over the XML document
     * @return {@link XMLProtectionResult#REWRITTEN} if a DTD was dropped and only the writer's copy
     * may be forwarded, {@link XMLProtectionResult#ACCEPTED} if the document may pass as it arrived,
     * otherwise {@link Rejected} naming the violation
     */
    public XMLProtectionResult protect(Reader in) {
        try {
            final XMLEventReader parser = inputFactory.createXMLEventReader(in);
            try {
                return copy(parser);
            } finally {
                closeParser(parser);
            }
        } catch (XMLStreamException e) {
            return Rejected.at("Not well-formed XML", e);
        } finally {
            closeWriter();
        }
    }

    /**
     * Copies the document to the writer event by event, and no further than the first violation.
     */
    private XMLProtectionResult copy(XMLEventReader parser) throws XMLStreamException {
        boolean dtdRemoved = false;
        while (parser.hasNext()) {
            XMLEvent event = parser.nextEvent();
            trackDepth(event);

            Rejected violation = check(event);
            if (violation != null)
                return violation;

            if (limits.removeDTD() && event instanceof DTD) {
                log.debug("Removed DTD.");
                dtdRemoved = true;
                continue;
            }

            if (event instanceof StartDocument startDocument) {
                writer.add(withActualEncoding(startDocument));
                continue;
            }

            writer.add(event);
        }
        writer.flush();
        return dtdRemoved ? REWRITTEN : ACCEPTED;
    }

    /**
     * The document's own XML declaration can name an encoding different from {@link #charset} - the
     * one {@code out} was actually opened with, chosen by {@code XMLProtectionInterceptor} from the
     * HTTP {@code Content-Type} charset when the client sent one. Forwarding the parsed
     * {@link StartDocument} event as is would then relabel the rewritten copy with an encoding it was
     * never written in, so the declaration is rebuilt to name {@link #charset} instead.
     */
    private XMLEvent withActualEncoding(StartDocument original) {
        String version = original.getVersion() != null ? original.getVersion() : "1.0";
        if (original.standaloneSet())
            return EVENT_FACTORY.createStartDocument(charset.name(), version, original.isStandalone());
        return EVENT_FACTORY.createStartDocument(charset.name(), version);
    }

    /**
     * Releases what the {@link XMLEventReader} holds, and only that - the {@link Reader} it was
     * parsing stays the caller's to close, as with {@link #closeWriter()}. A failure here cannot change the
     * outcome either, so it is only logged.
     */
    private static void closeParser(XMLEventReader parser) {
        try {
            parser.close();
        } catch (XMLStreamException e) {
            log.debug("Could not close the XML event reader.", e);
        }
    }

    /**
     * Releases what the {@link XMLEventWriter} holds, and only that - the {@link Writer} it was
     * handed stays the caller's to close. It flushes on the way out, so a rejected document leaves
     * its partial copy behind for the caller to discard.
     *
     * <p>A failure to close must not change the outcome, or a policy violation would surface as a
     * server error: by this point the copy has either been flushed already or belongs to a document
     * being rejected, so there is nothing left to salvage and the failure is only logged.</p>
     */
    private void closeWriter() {
        try {
            writer.close();
        } catch (XMLStreamException e) {
            log.debug("Could not close the XML event writer.", e);
        }
    }

    /**
     * Keeps {@link #depth} in step with the reader's position. Separate from {@link #check}, so that
     * nothing named {@code check...} changes state.
     */
    private void trackDepth(XMLEvent event) {
        if (event.isStartElement())
            depth++;
        else if (event.isEndElement())
            depth--;
    }

    /**
     * @return what the event violates, or {@code null} if it is within the configured policy
     */
    private @Nullable Rejected check(XMLEvent event) {
        return switch (event) {
            case StartElement startElement -> checkStartElement(startElement);
            case DTD dtd -> checkDtd(dtd);
            default -> null;
        };
    }

    private @Nullable Rejected checkStartElement(StartElement element) {
        int nameLength = nameLengthOf(element.getName());
        if (exceeds(nameLength, limits.maxElementNameLength()))
            return new Rejected("Element name of %d characters exceeds the limit of %d."
                    .formatted(nameLength, limits.maxElementNameLength()));

        Rejected attributeViolation = checkAttributes(element);
        if (attributeViolation != null)
            return attributeViolation;

        if (exceeds(depth, limits.maxDepth()))
            return new Rejected("Element nesting depth %d exceeds the limit of %d."
                    .formatted(depth, limits.maxDepth()));

        return null;
    }

    /**
     * Walks the attributes once, for their number and their names alike, and no further than the
     * first violation - so an element carrying a million attributes is rejected without walking all
     * of them.
     */
    private @Nullable Rejected checkAttributes(StartElement element) {
        var attributes = element.getAttributes();
        int count = 0;
        while (attributes.hasNext()) {
            Attribute attribute = attributes.next();

            if (exceeds(++count, limits.maxAttributeCount()))
                return new Rejected("Element %s has more than the %d allowed attributes."
                        .formatted(element.getName(), limits.maxAttributeCount()));

            int nameLength = nameLengthOf(attribute.getName());
            if (exceeds(nameLength, limits.maxAttributeNameLength()))
                return new Rejected("Attribute name of %d characters exceeds the limit of %d."
                        .formatted(nameLength, limits.maxAttributeNameLength()));
        }
        return null;
    }

    /**
     * The length of the name as the document spells it, {@code prefix:localName} - a prefix is as
     * attacker-controlled as the local name is, and measuring only the local part would let an
     * arbitrarily long prefix through.
     */
    private static int nameLengthOf(QName name) {
        int prefixLength = name.getPrefix().length();
        if (prefixLength == 0)
            return name.getLocalPart().length();
        return prefixLength + 1 + name.getLocalPart().length(); // + ':'
    }

    /**
     * A DOCTYPE is only a danger if it points outside the document. An external subset reference is
     * tolerated while the DTD is being removed: the reference is never resolved and the DOCTYPE does
     * not reach the backend, so only the attempt is logged.
     */
    private @Nullable Rejected checkDtd(DTD dtd) {
        if (containsExternalEntityReferences(dtd))
            return new Rejected("External entity declaration in DOCTYPE.");

        if (!hasExternalSubsetReference(dtd))
            return null;

        if (!limits.removeDTD())
            return new Rejected("External DTD subset reference in DOCTYPE declaration.");

        log.info("Possible attack. External DTD subset reference in DOCTYPE declaration (DTD removed, request continues).");
        log.debug("DTD: {}", dtd.getDocumentTypeDeclaration());
        return null;
    }
}
