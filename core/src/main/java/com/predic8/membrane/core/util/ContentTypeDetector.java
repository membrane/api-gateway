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

import com.google.common.collect.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.multipart.*;
import org.jetbrains.annotations.*;

import javax.xml.stream.*;
import java.util.*;

import static com.predic8.membrane.core.Constants.*;
import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.util.ContentTypeDetector.EffectiveContentType.*;
import static com.predic8.membrane.core.util.ContentTypeDetector.EffectiveContentType.UNKNOWN;
import static javax.xml.stream.XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES;
import static javax.xml.stream.XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES;
import static javax.xml.stream.XMLStreamConstants.*;

/**
 * This class tries to detect the "content type" of a given message.
 * "Content Type" here is more complex than the HTTP header "Content-Type": For
 * example a message of the effective type "SOAP" might be XOP-encoded and have
 * HTTP "Content-Type" "multipart/related".
 * Note that this class does not give a guarantee that the content is actually
 * valid.
 */
public class ContentTypeDetector {

    /**
     * ContentType this message effectively is (e.g. return
     * "SOAP", if the message is a multipart/related XOP-encoded
     * SOAP-message).
     */
    public enum EffectiveContentType {
        SOAP,
        XML,
        JSON,
        HTML,
        TEXT,
        UNKNOWN
    }

    private static final Set<String> contentTypesXML = ImmutableSet.of(
            "text/xml",
            "application/xml",
            "multipart/related");

    private static final Set<String> contentTypesHTML = ImmutableSet.of(
            "text/html");

    private static final Set<String> contentTypesJSON = ImmutableSet.of(
            APPLICATION_JSON,
            APPLICATION_X_JAVASCRIPT,
            TEXT_JAVASCRIPT,
            TEXT_X_JAVASCRIPT,
            TEXT_X_JSON,
            APPLICATION_PROBLEM_JSON);

    private static final XOPReconstitutor xopr = new XOPReconstitutor();

    public static EffectiveContentType detectEffectiveContentType(Message m) {
        try {
            jakarta.mail.internet.ContentType t = m.getHeader().getContentTypeObject();
            if (t == null)
                return UNKNOWN;

            String type = t.getPrimaryType() + "/" + t.getSubType();

            // JSON
            if (contentTypesJSON.contains(type))
                return JSON;

            // XML
            if (contentTypesXML.contains(type)) {
                return analyseXMLContent(m);
            }

            if (contentTypesHTML.contains(type)) {
                return HTML;
            }

            if (t.getPrimaryType().equals("text")) {
                return TEXT;
            }

            if (t.getSubType().endsWith("+json")) {
                return JSON;
            }

        } catch (Exception ignore) {
            // do nothing
        }
        return UNKNOWN;
    }

    private static @NotNull EffectiveContentType analyseXMLContent(Message m) {
        XMLStreamReader reader;

        try {
            // It is probably cheaper to create a factory than to synchronize on it. Also less risky.
            // See: https://stackoverflow.com/questions/21634315/is-xmlinputfactory-thread-safe
            reader = getXMLInputFactory().createXMLStreamReader(xopr.reconstituteIfNecessary(m));

            if (reader.nextTag() == START_ELEMENT) {
                if (isSOAP(reader))
                    return SOAP;
            }
        } catch (Exception ignored) {
        }
        return XML;
    }

    private static boolean isSOAP(XMLStreamReader reader) {
        return SOAP11_NS.equals(reader.getNamespaceURI()) ||
               SOAP12_NS.equals(reader.getNamespaceURI());
    }

    private static XMLInputFactory getXMLInputFactory() {
        XMLInputFactory f = XMLInputFactory.newInstance();
        f.setProperty(IS_REPLACING_ENTITY_REFERENCES, false);
        f.setProperty(IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        return f;
    }
}