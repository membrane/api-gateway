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

package com.predic8.membrane.core.exceptions;

import com.predic8.membrane.core.http.Response;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;
import java.util.Map;
import java.util.regex.Pattern;

import static com.predic8.membrane.core.http.MimeType.APPLICATION_PROBLEM_XML;
import static javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING;
import static javax.xml.transform.OutputKeys.*;

public class ProblemDetailsXML {

    /**
     * XML Name production rule (simplified): starts with letter or '_', followed by
     * letters, digits, '.', '-', '_', ':'.
     * Keys that contain '/', '#', spaces, etc. are not valid XML element names.
     */
    private static final Pattern VALID_XML_NAME = Pattern.compile("[a-zA-Z_][a-zA-Z0-9.\\-_:]*");

    private static boolean isValidXmlName(String name) {
        return name != null && !name.isEmpty() && VALID_XML_NAME.matcher(name).matches();
    }

    static void createXMLContent(Map<String, Object> root, Response.ResponseBuilder builder) throws Exception {
        builder.body(convertMapToXml(root));
        builder.contentType(APPLICATION_PROBLEM_XML);
    }

    private static String convertMapToXml(Map<String, Object> map) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setFeature(FEATURE_SECURE_PROCESSING, true);
        Document doc = dbf.newDocumentBuilder().newDocument();
        Element root = doc.createElement("problem-details");
        doc.appendChild(root);
        mapToXmlElements(map, doc, root);
        return document2string(doc);
    }

    private static String document2string(Document document) throws TransformerException {
        StringWriter writer = new StringWriter();
        TransformerFactory tf = TransformerFactory.newInstance();
        tf.setFeature(FEATURE_SECURE_PROCESSING, true);
        Transformer t = tf.newTransformer();
        t.setOutputProperty(INDENT, "yes");
        t.setOutputProperty(ENCODING, "UTF-8");
        t.setOutputProperty(OMIT_XML_DECLARATION, "no");
        t.transform(new DOMSource(document), new StreamResult(writer));
        return writer.toString();
    }

    private static void mapToXmlElements(Map<String, Object> map, Document document, Element parent) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            Object value = entry.getValue();
            if (value == null)
                continue;
            String key = entry.getKey();

            if (value instanceof Map<?, ?> mv) {
                Element element = createElement(document, key);
                @SuppressWarnings("unchecked")
                Map<String, Object> nested = (Map<String, Object>) mv;
                mapToXmlElements(nested, document, element);
                parent.appendChild(element);
            } else if (value instanceof java.util.Collection<?> col) {
                for (Object obj : col) {
                    if (obj == null) continue;
                    Element arrayElement = createElement(document, key);
                    if (obj instanceof Map<?,?> objMap) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> nested = (Map<String, Object>) objMap;
                        mapToXmlElements(nested, document, arrayElement);
                    } else {
                        arrayElement.setTextContent(String.valueOf(obj));
                    }
                    parent.appendChild(arrayElement);
                }
            } else if (value.getClass().isArray()) {
                int len = java.lang.reflect.Array.getLength(value);
                for (int i = 0; i < len; i++) {
                    Object obj = java.lang.reflect.Array.get(value, i);
                    if (obj == null) continue;
                    Element arrayElement = createElement(document, key);
                    arrayElement.setTextContent(String.valueOf(obj));
                    parent.appendChild(arrayElement);
                }
            } else {
                Element element = createElement(document, key);
                element.setTextContent(String.valueOf(value));
                parent.appendChild(element);
            }
        }
    }

    /**
     * Creates an element whose tag name is {@code key} when {@code key} is a valid XML name,
     * or an {@code <entry key="...">} wrapper element otherwise.
     * The element is NOT yet appended to {@code parent} — the caller appends it.
     */
    private static Element createElement(Document document, String key) {
        if (isValidXmlName(key)) {
            return document.createElement(key);
        }
        // Fall back to <entry key="..."> for keys that aren't valid XML names
        // (e.g. "REQUEST/BODY#/id" from OpenAPI validation errors)
        var entry = document.createElement("entry");
        entry.setAttribute("key", key);
        return entry;
    }
}
