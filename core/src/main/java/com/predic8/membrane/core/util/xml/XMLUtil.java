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

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.namespace.QName;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;

import static javax.xml.transform.OutputKeys.*;

public class XMLUtil {

    /**
     * {@code newDefaultInstance()} pins the JDK's built-in implementation, so the serialized output
     * does not change when another {@link TransformerFactory} (e.g. Saxon) is on the classpath, and
     * it skips the classpath provider lookup of {@code newInstance()}.
     * <p>
     * {@link TransformerFactory} is not specified as thread-safe, hence one per thread.
     */
    private static final ThreadLocal<TransformerFactory> SERIALIZER_FACTORY = ThreadLocal.withInitial(TransformerFactory::newDefaultInstance);

    /**
     * Returns a new identity {@link Transformer} for serializing a DOM {@link Node}
     * ({@code DOMSource}) to a {@code StreamResult}. Not thread-safe; create one per serialization
     * or per thread.
     * <p>
     * The factory is deliberately not hardened: an identity transform of an in-memory DOM parses
     * nothing and loads no stylesheet, and a DOCTYPE or entity reference in the DOM is only written
     * out, never fetched. Do not use it to transform a {@code StreamSource} or to run a stylesheet.
     */
    public static Transformer newSerializer() throws TransformerConfigurationException {
        return SERIALIZER_FACTORY.get().newTransformer();
    }

    public static String xmlNode2String(Node node) throws TransformerException {
        if (node == null) {
            return "";
        }

        Transformer tf = newSerializer();
        tf.setOutputProperty(OMIT_XML_DECLARATION, "yes");
        tf.setOutputProperty(METHOD, "xml");
        tf.setOutputProperty(INDENT, "yes");

        StringWriter writer = new StringWriter();
        tf.transform(new DOMSource(node), new StreamResult(writer));
        return writer.toString();
    }

    public static QName groovyToJavaxQName(groovy.namespace.QName qName) {
        return new QName(qName.getNamespaceURI(), qName.getLocalPart(), qName.getPrefix());
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
