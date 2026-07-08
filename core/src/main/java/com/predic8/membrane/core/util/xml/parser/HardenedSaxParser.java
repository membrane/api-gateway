/* Copyright 2025 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.util.xml.parser;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.StringReader;

import static javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING;

/**
 * Thread-safe, XXE-hardened SAX parser factory.
 * A fresh {@link SAXParser} is created for each call to {@link #newSAXParser()} from a
 * per-thread {@link SAXParserFactory}, so parser creation never contends on a shared lock.
 * <p>
 * Why a ThreadLocal and not a shared factory or a parser pool: Membrane runs one virtual
 * thread per connection, so the cached factory is reused across all keep-alive requests on
 * a connection (and fully reused with platform threads). A {@code synchronized} shared
 * factory would pin virtual threads to their carrier on Java 21. A parser pool would save
 * only a few microseconds per message — noise next to the actual parse/validation work —
 * while requiring re-hardening after {@link SAXParser#reset()}, which drops the entity
 * resolver set below. Worst case here is one factory creation (~20 µs) per connection.
 */
public final class HardenedSaxParser {

    private static final ThreadLocal<SAXParserFactory> FACTORY = ThreadLocal.withInitial(HardenedSaxParser::createFactory);

    private HardenedSaxParser() {}

    private static SAXParserFactory createFactory() {
        SAXParserFactory f = SAXParserFactory.newInstance();
        f.setNamespaceAware(true);
        f.setValidating(false);

        try {
            f.setFeature(FEATURE_SECURE_PROCESSING, true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setFeature("http://xml.org/sax/features/external-general-entities", false);
            f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        } catch (ParserConfigurationException | SAXNotRecognizedException | SAXNotSupportedException e) {
            throw new IllegalStateException("Secure SAX parser features not supported", e);
        }

        return f;
    }

    /**
     * Creates a new hardened {@link SAXParser}. Not thread-safe — each caller must use their own instance.
     */
    public static SAXParser newSAXParser() {
        try {
            SAXParser parser = FACTORY.get().newSAXParser();
            // Second line of defence: if disallow-doctype-decl is ever ignored by the SAX
            // implementation, the resolver swallows the external fetch instead of hitting the network.
            parser.getXMLReader().setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
            return parser;
        } catch (ParserConfigurationException | SAXException e) {
            throw new XmlParseException("Failed to create SAX parser: " + e.getMessage(), e);
        }
    }
}
