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

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.stream.*;
import javax.xml.stream.events.DTD;
import javax.xml.stream.events.EntityDeclaration;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static com.predic8.membrane.core.util.CollectionsUtil.count;
import static com.predic8.membrane.core.util.xml.parser.HardenedStaxInputFactory.JAVAX_XML_STREAM_IS_SUPPORTING_EXTERNAL_ENTITIES;
import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static javax.xml.stream.XMLInputFactory.*;

/**
 * Filters XML streams, removing potentially malicious elements:
 * <ul>
 * <li>DTDs can be removed.</li>
 * <li>The length of element names can be limited.</li>
 * <li>The number of attributes per element can be limited.</li>
 * </ul>
 * <p>
 * If {@link #protect(InputStreamReader)} returns false, an unrecoverable error has
 * occurred (such as not-wellformed XML or an element name length exceeded the limit),
 * the {@link OutputStreamWriter} is left at this position: It should be discarded and
 * an error response should be returned to the requestor.
 */
public class XMLProtector {
    private static final Logger log = LoggerFactory.getLogger(XMLProtector.class);

    // Word-bounded so DOCTYPE names that merely contain the keywords (e.g. PUBLICATIONS) don't match
    private static final Pattern EXTERNAL_ID_KEYWORD = Pattern.compile("\\b(?:SYSTEM|PUBLIC)\\b");

    private final XMLEventWriter writer;
    private final int maxAttributeCount;
    private final int maxElementNameLength;
    private final boolean removeDTD;

    /**
     * Use own XMLInputFactory with settings that might be insecure for other applications.
     * Creating 1.000.000 XMLInputFactory takes 10s, using ThreadLocal 0s
     */
    private final ThreadLocal<XMLInputFactory> xmlInputFactoryFactory;

    public XMLProtector(OutputStreamWriter osw, boolean removeDTD, int maxElementNameLength, int maxAttributeCount) throws Exception {
        this.writer = XMLOutputFactory.newInstance().createXMLEventWriter(osw);
        this.removeDTD = removeDTD;
        this.maxElementNameLength = maxElementNameLength;
        this.maxAttributeCount = maxAttributeCount;
        xmlInputFactoryFactory = ThreadLocal.withInitial(this::getXmlInputFactory);
    }

    private static void checkExternalEntities(DTD dtd) throws XMLProtectionException {
        if (containsExternalEntityReferences(dtd)) {
            String msg = "Possible attack. External entity found in DTD.";
            log.info(msg);
            throw new XMLProtectionException(msg);
        }
    }

    private static void checkExternalSubset(DTD dtd) throws XMLProtectionException {
        if (hasExternalSubsetReference(dtd)) {
            String msg = "Possible attack. External DTD subset reference in DOCTYPE declaration.";
            log.info(msg);
            log.debug("DTD: {}", dtd.getDocumentTypeDeclaration());
            throw new XMLProtectionException(msg);
        }
    }

    private static boolean hasExternalSubsetReference(DTD dtd) {
        var decl = dtd.getDocumentTypeDeclaration();
        if (decl == null) return false;
        // Only inspect the header before the internal subset '[' — SYSTEM/PUBLIC only appear there as keywords
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

    private static boolean containsExternalEntityReferences(DTD dtd) {
        var entities = dtd.getEntities();
        if (entities == null || entities.isEmpty())
            return false;

        return entities.stream().anyMatch(isExternalEntity());
    }

    private static @NotNull Predicate<EntityDeclaration> isExternalEntity() {
        return ed -> ed.getPublicId() != null || ed.getSystemId() != null;
    }

    /**
     * Is XML secure?
     *
     * @param isr Stream with XML
     * @return false if there is any security problem in the XML
     * @throws XMLProtectionException if there are critical issues like external entity references
     */
    public boolean protect(InputStreamReader isr) throws XMLProtectionException {
        try {
            XMLEventReader parser = xmlInputFactoryFactory.get().createXMLEventReader(isr);

            while (parser.hasNext()) {
                XMLEvent event = parser.nextEvent();
                if (event.isStartElement()) {
                    StartElement startElement = event.asStartElement();
                    if (!checkElementNameLength(startElement))
                        return false;
                    if (!checkNumberOfAttributes(startElement))
                        return false;
                }
                if (event instanceof javax.xml.stream.events.DTD dtd) {
                    checkExternalEntities(dtd);
                    if (removeDTD) {
                        if (hasExternalSubsetReference(dtd)) {
                            log.info("Possible attack. External DTD subset reference in DOCTYPE declaration (DTD removed, request continues).");
                            log.debug("DTD: {}", dtd.getDocumentTypeDeclaration());
                        }
                        log.debug("removed DTD.");
                        continue;
                    }
                    checkExternalSubset(dtd);
                }
                writer.add(event);
            }
            writer.flush();
        } catch (XMLStreamException e) {
            Location loc = e.getLocation();
            log.warn("Received not well-formed XML at line {}, column {}: {}",
                    loc != null ? loc.getLineNumber() : -1,
                    loc != null ? loc.getColumnNumber() : -1,
                    e.getMessage());
            return false;
        }
        return true;
    }

    private boolean checkNumberOfAttributes(StartElement startElement) {
        if (maxAttributeCount < 0)
            return true;

        var numberOfAttributes = count(startElement.getAttributes());
        if (numberOfAttributes > maxAttributeCount) {
            log.warn("Element {} has {} attributes. Exceeding limit of {}", startElement.getName(), maxAttributeCount, maxAttributeCount);
            return false;
        }

        return true;
    }

    private boolean checkElementNameLength(StartElement startElement) {
        var elementLenght = startElement.getName().getLocalPart().length();
        if (maxElementNameLength != -1 &&
            elementLenght > maxElementNameLength) {
            log.warn("Element with {} characters exceeds limit of {}", elementLenght, maxElementNameLength);
            return false;
        }
        return true;
    }

    private XMLInputFactory getXmlInputFactory() {
        // Return a new, fully hardened XMLInputFactory instance. That is not shared
        XMLInputFactory f = XMLInputFactory.newInstance();
        // hardening
        f.setProperty(IS_COALESCING, FALSE);

        // Support DTDs on purpose to detect them in the StAX loop!
        f.setProperty(SUPPORT_DTD, TRUE);

        // Block external DTD fetches while still surfacing the DTD event for removal
        f.setXMLResolver((publicId, systemId, baseURI, namespace) -> new ByteArrayInputStream(new byte[0]));

        f.setProperty(IS_NAMESPACE_AWARE, TRUE);
        f.setProperty(IS_REPLACING_ENTITY_REFERENCES, FALSE);
        try {
            f.setProperty(JAVAX_XML_STREAM_IS_SUPPORTING_EXTERNAL_ENTITIES, FALSE);
        } catch (IllegalArgumentException ignored) {
        }
        try {
            f.setProperty(IS_VALIDATING, FALSE);
        } catch (IllegalArgumentException ignored) {
        }
        return f;
    }
}
