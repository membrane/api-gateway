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

import com.predic8.membrane.core.http.*;
import org.w3c.dom.*;

import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.*;
import javax.xml.transform.stream.*;
import java.io.*;
import java.util.*;

import static com.predic8.membrane.core.http.MimeType.*;
import static javax.xml.XMLConstants.*;
import static javax.xml.transform.OutputKeys.*;

public class ProblemDetailsXML {

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
            String name = entry.getKey();
            if (value instanceof Map<?, ?> mv) {
                Element element = document.createElement(name);
                @SuppressWarnings("unchecked")
                Map<String, Object> nested = (Map<String, Object>) mv;
                mapToXmlElements(nested, document, element);
                parent.appendChild(element);
            } else if (value instanceof java.util.Collection<?> col) {
                for (Object obj : col) {
                    if (obj == null) continue;
                    Element arrayElement = document.createElement(name);
                    arrayElement.setTextContent(String.valueOf(obj));
                    parent.appendChild(arrayElement);
                }
            } else if (value.getClass().isArray()) {
                int len = java.lang.reflect.Array.getLength(value);
                for (int i = 0; i < len; i++) {
                    Object obj = java.lang.reflect.Array.get(value, i);
                    if (obj == null) continue;
                    Element arrayElement = document.createElement(name);
                    arrayElement.setTextContent(String.valueOf(obj));
                    parent.appendChild(arrayElement);
                }
            } else {
                Element element = document.createElement(name);
                element.setTextContent(String.valueOf(value));
                parent.appendChild(element);
            }
        }
    }
}
