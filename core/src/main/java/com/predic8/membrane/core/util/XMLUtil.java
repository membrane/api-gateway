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
package com.predic8.membrane.core.util;

import com.predic8.membrane.core.http.*;
import org.jetbrains.annotations.*;
import org.w3c.dom.*;
import org.xml.sax.*;

import javax.xml.namespace.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.*;
import javax.xml.transform.stream.*;
import java.io.*;
import java.util.*;

import static javax.xml.transform.OutputKeys.*;

public class XMLUtil {

    public static String xml2string(Node doc) throws TransformerException {
        TransformerFactory tfFactory = TransformerFactory.newInstance(); // Comment ThreadSafe? with URL
        Transformer tf = tfFactory.newTransformer();
        tf.setOutputProperty(OMIT_XML_DECLARATION, "yes");

        tf.setOutputProperty(INDENT, "yes");
        tf.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

        StringWriter writer = new StringWriter();
        tf.transform(new DOMSource(doc), new StreamResult(writer));
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
