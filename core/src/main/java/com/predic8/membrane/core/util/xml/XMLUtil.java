/* Copyright 2024 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.util.xml;

import com.predic8.membrane.core.http.*;
import org.jetbrains.annotations.*;
import org.w3c.dom.*;
import org.xml.sax.*;

import javax.xml.*;
import javax.xml.namespace.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.*;
import javax.xml.transform.stream.*;
import java.io.*;
import java.util.*;

import static javax.xml.XMLConstants.*;
import static javax.xml.transform.OutputKeys.*;

public class XMLUtil {

    // TransformerFactory is *not* specified as thread-safe.
    // We keep one instance per thread. See:
    // https://docs.oracle.com/javase/8/docs/api/javax/xml/transform/TransformerFactory.html
    private static final ThreadLocal<TransformerFactory> TF = ThreadLocal.withInitial(() -> {
        TransformerFactory f = TransformerFactory.newInstance();
        try {
            // Enable secure processing (limits entity expansion etc.)
            f.setFeature(FEATURE_SECURE_PROCESSING, true);
        } catch (TransformerConfigurationException | TransformerFactoryConfigurationError e) {
            // Log if you want, but do not fail hard because of missing feature
        }

        try {
            // Disallow access to external DTDs and stylesheets (JAXP 1.5+)
            f.setAttribute(ACCESS_EXTERNAL_DTD, "");
            f.setAttribute(ACCESS_EXTERNAL_STYLESHEET, "");
        } catch (IllegalArgumentException ignored) {
            // Attributes not supported by all implementations
        }

        return f;
    });


    public static String xmlNode2String(Node node) throws TransformerException {
        if (node == null) {
            return "";
        }

        Transformer tf = TF.get().newTransformer();
        tf.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        tf.setOutputProperty(OutputKeys.METHOD, "xml");
        tf.setOutputProperty(OutputKeys.INDENT, "yes");

        StringWriter writer = new StringWriter();
        tf.transform(new DOMSource(node), new StreamResult(writer));
        return writer.toString();
    }

    public static QName groovyToJavaxQName(groovy.namespace.QName qName) {
        return new QName(qName.getNamespaceURI(), qName.getLocalPart(), qName.getPrefix());
    }

    /**
     * For XML processing sometimes an InputSource is needed.
     * @param msg
     * @return InputSource of the message body
     */
    public static @NotNull InputSource getInputSource(Message msg) {
        return new InputSource(new InputStreamReader(msg.getBodyAsStreamDecoded()));
    }

    public static void mapToXml(Document doc, Element parent, Map<String, Object> map) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            Element elem = doc.createElement(entry.getKey());
            Object value = entry.getValue();

            if (value instanceof Map) {
                // Recursively handle nested maps
                mapToXml(doc, elem, (Map<String, Object>) value);
            } else if (value instanceof List) {
                // Handle lists
                for (Object item : (List<?>) value) {
                    Element itemElement = doc.createElement("item");
                    if (item instanceof Map) {
                        mapToXml(doc, itemElement, (Map<String, Object>) item);
                    } else {
                        itemElement.setTextContent(item.toString());
                    }
                    elem.appendChild(itemElement);
                }
            } else {
                // Handle primitive values
                elem.setTextContent(value.toString());
            }

            parent.appendChild(elem);
        }
    }
}
