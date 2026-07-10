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
 * A fresh {@link SAXParser} is created for each call to {@link #newSAXParser()}.
 * Instances are immutable and can safely be shared across threads.
 */
public final class HardenedSaxParser {

    private final SAXParserFactory factory = createFactory();

    private static volatile HardenedSaxParser INSTANCE;

    private HardenedSaxParser() {}

    public static HardenedSaxParser getInstance() {
        if (INSTANCE == null) {
            synchronized (HardenedSaxParser.class) {
                if (INSTANCE == null) {
                    INSTANCE = new HardenedSaxParser();
                }
            }
        }
        return INSTANCE;
    }

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
    public SAXParser newSAXParser() {
        try {
            SAXParser parser = factory.newSAXParser();
            parser.getXMLReader().setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
            return parser;
        } catch (ParserConfigurationException | SAXException e) {
            throw new XmlParseException("Failed to create SAX parser: " + e.getMessage(), e);
        }
    }
}
