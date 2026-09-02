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
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.namespace.QName;
import javax.xml.stream.*;
import javax.xml.stream.events.*;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.List;
import java.util.regex.Pattern;

import static com.predic8.membrane.core.interceptor.xmlprotection.XMLLimits.exceeds;
import static com.predic8.membrane.core.interceptor.xmlprotection.XMLProtectionResult.ACCEPTED;

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
 * A {@link Rejected} result means the copy stopped mid-document: the {@link OutputStreamWriter} is
 * left at that position, so its content has to be discarded and an error returned to the requestor.
 * <p>
 * One instance scans one document; it keeps the nesting depth of that document as state.
 */
public class XMLProtector {
    private static final Logger log = LoggerFactory.getLogger(XMLProtector.class);

    /** Writers are per document, the factory that makes them is not. */
    private static final XMLOutputFactory OUTPUT_FACTORY = XMLOutputFactory.newInstance();

    // Word-bounded so DOCTYPE names that merely contain the keywords (e.g. PUBLICATIONS) don't match
    private static final Pattern EXTERNAL_ID_KEYWORD = Pattern.compile("\\b(?:SYSTEM|PUBLIC)\\b");

    private final XMLEventWriter writer;
    private final XMLInputFactory inputFactory;
    private final XMLLimits limits;

    /** Nesting depth of the element the reader is currently in. */
    private int depth;

    /**
     * @param inputFactory a DTD-aware, hardened factory, see
     *                     {@link com.predic8.membrane.core.util.xml.parser.HardenedStaxInputFactory#dtdAwareInputFactory()}
     */
    public XMLProtector(OutputStreamWriter osw, XMLInputFactory inputFactory, XMLLimits limits) throws XMLStreamException {
        this.writer = OUTPUT_FACTORY.createXMLEventWriter(osw);
        this.inputFactory = inputFactory;
        this.limits = limits;
    }

    /**
     * Copies the document to the writer, dropping the DTD if configured to, and stops at the first
     * violation.
     *
     * @param isr stream with the XML document
     * @return {@link XMLProtectionResult#ACCEPTED} if the document may be passed on, otherwise
     * {@link Rejected} naming the violation
     */
    public XMLProtectionResult protect(InputStreamReader isr) {
        try {
            XMLEventReader parser = inputFactory.createXMLEventReader(isr);

            while (parser.hasNext()) {
                XMLEvent event = parser.nextEvent();
                trackDepth(event);

                if (check(event) instanceof Rejected rejected)
                    return rejected;

                if (limits.removeDTD() && event instanceof DTD) {
                    log.debug("Removed DTD.");
                    continue;
                }
                writer.add(event);
            }
            writer.flush();
            return ACCEPTED;
        } catch (XMLStreamException e) {
            return notWellFormed(e);
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

    private XMLProtectionResult check(XMLEvent event) {
        return switch (event) {
            case StartElement startElement -> checkStartElement(startElement);
            case DTD dtd -> checkDtd(dtd);
            default -> ACCEPTED;
        };
    }

    private XMLProtectionResult checkStartElement(StartElement element) {
        int nameLength = nameLengthOf(element.getName());
        if (exceeds(nameLength, limits.maxElementNameLength()))
            return new Rejected("Element name of %d characters exceeds the limit of %d."
                    .formatted(nameLength, limits.maxElementNameLength()));

        if (checkAttributes(element) instanceof Rejected rejected)
            return rejected;

        if (exceeds(depth, limits.maxDepth()))
            return new Rejected("Element nesting depth %d exceeds the limit of %d."
                    .formatted(depth, limits.maxDepth()));

        return ACCEPTED;
    }

    /**
     * Walks the attributes once, for their number and their names alike, and no further than the
     * first violation - so an element carrying a million attributes is rejected without walking all
     * of them.
     */
    private XMLProtectionResult checkAttributes(StartElement element) {
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
        return ACCEPTED;
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
    private XMLProtectionResult checkDtd(DTD dtd) {
        if (containsExternalEntityReferences(dtd))
            return new Rejected("External entity declaration in DOCTYPE.");

        if (!hasExternalSubsetReference(dtd))
            return ACCEPTED;

        if (!limits.removeDTD())
            return new Rejected("External DTD subset reference in DOCTYPE declaration.");

        log.info("Possible attack. External DTD subset reference in DOCTYPE declaration (DTD removed, request continues).");
        log.debug("DTD: {}", dtd.getDocumentTypeDeclaration());
        return ACCEPTED;
    }

    private static XMLProtectionResult notWellFormed(XMLStreamException e) {
        Location loc = e.getLocation();
        return new Rejected("Not well-formed XML at line %d, column %d: %s".formatted(
                loc != null ? loc.getLineNumber() : -1,
                loc != null ? loc.getColumnNumber() : -1,
                e.getMessage()));
    }

    @SuppressWarnings("unchecked") // DTD.getEntities() is a raw List of EntityDeclaration by contract
    private static boolean containsExternalEntityReferences(DTD dtd) {
        List<EntityDeclaration> entities = dtd.getEntities();
        return entities != null && entities.stream()
                .anyMatch(entity -> entity.getPublicId() != null || entity.getSystemId() != null);
    }

    private static boolean hasExternalSubsetReference(DTD dtd) {
        var decl = dtd.getDocumentTypeDeclaration();
        if (decl == null) return false;
        // Only inspect the header before the internal subset '[' - SYSTEM/PUBLIC only appear there as keywords
        return EXTERNAL_ID_KEYWORD.matcher(getHeaderAfterRootName(getHeader(decl))).find();
    }

    private static @NotNull String getHeader(String decl) {
        int internalSubset = decl.indexOf('[');
        return internalSubset >= 0 ? decl.substring(0, internalSubset) : decl;
    }

    /**
     * Per the {@link DTD#getDocumentTypeDeclaration()} contract, the header always starts with
     * "DOCTYPE" followed by the declared root element name (e.g. "&lt;!DOCTYPE SYSTEM ..."). That
     * name can legally be "SYSTEM" or "PUBLIC" itself, so it must be skipped before keyword-matching
     * for an actual external identifier — otherwise such a root name would be mistaken for one.
     */
    static @NotNull String getHeaderAfterRootName(String header) {
        int doctypeIdx = header.indexOf("DOCTYPE");
        int i = doctypeIdx >= 0 ? doctypeIdx + "DOCTYPE".length() : 0;
        while (i < header.length() && Character.isWhitespace(header.charAt(i))) i++;
        while (i < header.length() && !Character.isWhitespace(header.charAt(i))) i++;
        return header.substring(i);
    }
}
