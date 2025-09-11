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

import static com.predic8.membrane.core.http.MimeType.APPLICATION_PROBLEM_XML;
import static javax.xml.transform.OutputKeys.INDENT;

public class ProblemDetailsXML {

    static void createXMLContent(Map<String, Object> root, Response.ResponseBuilder builder) throws Exception {
        builder.body(convertMapToXml(root));
        builder.contentType(APPLICATION_PROBLEM_XML);
    }

    private static String convertMapToXml(Map<String, Object> map) throws Exception {
        Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element root = document.createElement("problem-details");
        document.appendChild(root);
        mapToXmlElements(map, document, root);
        return document2string(document);
    }

    private static String document2string(Document document) throws TransformerException {
        StringWriter writer = new StringWriter();
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer t = tf.newTransformer();
        t.setOutputProperty(INDENT, "yes");
        t.transform(new DOMSource(document), new StreamResult(writer));
        return writer.toString();
    }

    private static void mapToXmlElements(Map<String, Object> map, Document document, Element parent) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            Object value = entry.getValue();
            if (value == null)
                continue;
            Element element = document.createElement(entry.getKey());
            if (value instanceof Map mv) {
                mapToXmlElements(mv, document, element);
            } else if (value instanceof Object[] oa) {
                for (Object obj : oa) {
                    Element arrayElement = document.createElement(entry.getKey());
                    arrayElement.setTextContent(obj.toString());
                    parent.appendChild(arrayElement);
                }
                continue;
            } else {
                element.setTextContent(value.toString());
            }
            parent.appendChild(element);
        }
    }
}
