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

import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import javax.xml.parsers.*;
import java.io.StringReader;

import static javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING;

/**
 * Thread-safe, XXE-hardened XML parser implementation.
 * A fresh {@link DocumentBuilder} is created for each call.
 * Instances are immutable and can safely be shared across threads.
 */
public final class HardenedXmlParser implements XmlParser {

    private final DocumentBuilderFactory factory = createFactory();

    private static XmlParser INSTANCE;

    private HardenedXmlParser() {}

    public static XmlParser getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new HardenedXmlParser();
            return INSTANCE;
        }

        return INSTANCE;
    }

    private static DocumentBuilderFactory createFactory() {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);

        try {
            // XXE protection and secure defaults
            f.setFeature(FEATURE_SECURE_PROCESSING, true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setFeature("http://xml.org/sax/features/external-general-entities", false);
            f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException("Secure XML parser features not supported", e);
        }

        try { f.setXIncludeAware(false); } catch (UnsupportedOperationException ignore) {}
        f.setExpandEntityReferences(false);
        return f;
    }

    private DocumentBuilder newBuilder() {
        try {
            DocumentBuilder builder = factory.newDocumentBuilder();
            // Prevent any external entity resolution
            builder.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
            return builder;
        } catch (ParserConfigurationException e) {
            throw new XmlParseException("Failed to create XML parser: " + e.getMessage(), e);
        }
    }

    @Override
    public Document parse(InputSource source) {
        if (source == null)
            throw new IllegalArgumentException("source must not be null");

        try {
            return newBuilder().parse(source);
        } catch (Exception e) {
            throw new XmlParseException("Could not parse XML document: " + e.getMessage()); // No stacktrace needed
        }
    }
}

