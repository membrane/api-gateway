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

import org.jetbrains.annotations.*;
import org.slf4j.*;

import javax.xml.stream.*;
import javax.xml.stream.events.*;
import java.io.*;
import java.util.*;
import java.util.function.*;

import static com.predic8.xml.beautifier.XMLInputFactoryFactory.*;
import static java.lang.Boolean.*;
import static javax.xml.stream.XMLInputFactory.*;

/**
 * Filters XML streams, removing potentially malicious elements:
 * <ul>
 * <li>DTDs can be removed.</li>
 * <li>The length of element names can be limited.</li>
 * <li>The number of attibutes per element can be limited.</li>
 * </ul>
 * <p>
 * If {@link #protect(InputStreamReader)} returns false, an unrecoverable error has
 * occurred (such as not-wellformed XML or an element name length exceeded the limit),
 * the {@link OutputStreamWriter} is left at this position: It should be discarded and
 * an error response should be returned to the requestor.
 */
public class XMLProtector {
    private static final Logger log = LoggerFactory.getLogger(XMLProtector.class.getName());

    private final XMLEventWriter writer;
    private final int maxAttibuteCount;
    private final int maxElementNameLength;
    private final boolean removeDTD;

    /**
     * Use own XMLInputFactory with settings that might be insecure for other applications
     */
    private final ThreadLocal<XMLInputFactory> xmlInputFactoryFactory;

    public XMLProtector(OutputStreamWriter osw, boolean removeDTD, int maxElementNameLength, int maxAttibuteCount) throws Exception {
        this.writer = XMLOutputFactory.newInstance().createXMLEventWriter(osw);
        this.removeDTD = removeDTD;
        this.maxElementNameLength = maxElementNameLength;
        this.maxAttibuteCount = maxAttibuteCount;

        xmlInputFactoryFactory = ThreadLocal.withInitial(this::getXmlInputFactory);
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
                    StartElement startElement = (StartElement) event;
                    if (maxElementNameLength != -1)
                        if (startElement.getName().getLocalPart().length() > maxElementNameLength) {
                            log.warn("Element name length: Limit exceeded.");
                            return false;
                        }
                    if (maxAttibuteCount != -1) {
                        Iterator<?> attrs = startElement.getAttributes();
                        int attributeCount = 0;
                        while (attrs.hasNext()) {
                            attrs.next();
                            attributeCount++;
                            if (attributeCount > maxAttibuteCount) {
                                log.warn("Number of attributes per element: Limit exceeded.");
                                return false;
                            }
                        }
                    }
                }
                if (event instanceof javax.xml.stream.events.DTD dtd) {
                    checkExternalEntities(dtd);
                    if (removeDTD) {
                        log.debug("removed DTD.");
                        continue;
                    }
                }
                writer.add(event);
            }
            writer.flush();
        } catch (XMLStreamException e) {
            log.warn("Received not well-formed XML: {}", e.getMessage());
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

    private static void checkExternalEntities(DTD dtd) throws XMLProtectionException {
        if (containsExternalEntityReferences(dtd)) {
            String msg = "Possible attack. External entity found in DTD.";
            log.warn(msg);
            throw new XMLProtectionException(msg);
        }
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
}
