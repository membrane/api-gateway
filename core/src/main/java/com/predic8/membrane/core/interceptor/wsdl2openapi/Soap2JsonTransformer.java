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

package com.predic8.membrane.core.interceptor.wsdl2openapi;

import com.fasterxml.jackson.databind.*;
import com.predic8.membrane.core.util.wsdl.parser.*;
import org.slf4j.*;
import org.w3c.dom.*;

import javax.xml.parsers.*;
import java.io.*;
import java.util.*;

/**
 * Transforms SOAP XML response to JSON
 */
public class Soap2JsonTransformer {

    private static final Logger log = LoggerFactory.getLogger(Soap2JsonTransformer.class);
    private final Definitions definitions;
    private final String operationName;
    private final ObjectMapper mapper = new ObjectMapper();

    public Soap2JsonTransformer(Definitions definitions, String operationName) {
        this.definitions = definitions;
        this.operationName = operationName;
    }

    public String transform(String soapXml) throws Exception {
        // Parse SOAP XML
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new ByteArrayInputStream(soapXml.getBytes("UTF-8")));

        // Extract response from SOAP Body
        Element body = getSoapBody(doc);
        if (body == null) {
            throw new IllegalArgumentException("No SOAP Body found in response");
        }

        // Get the first child element in Body (the response element)
        Element responseElement = getFirstChildElement(body);
        if (responseElement == null) {
            throw new IllegalArgumentException("No response element found in SOAP Body");
        }

        // Convert to JSON
        Map<String, Object> jsonMap = elementToMap(responseElement);
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonMap);
    }

    private Element getSoapBody(Document doc) {
        // Try SOAP 1.1 namespace
        NodeList bodies = doc.getElementsByTagNameNS("http://schemas.xmlsoap.org/soap/envelope/", "Body");
        if (bodies.getLength() > 0) {
            return (Element) bodies.item(0);
        }

        // Try SOAP 1.2 namespace
        bodies = doc.getElementsByTagNameNS("http://www.w3.org/2003/05/soap-envelope", "Body");
        if (bodies.getLength() > 0) {
            return (Element) bodies.item(0);
        }

        return null;
    }

    private Element getFirstChildElement(Element parent) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                return (Element) node;
            }
        }
        return null;
    }

    private Map<String, Object> elementToMap(Element element) {
        Map<String, Object> result = new LinkedHashMap<>();
        
        NodeList children = element.getChildNodes();
        Map<String, List<Object>> childGroups = new LinkedHashMap<>();

        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) node;
                String localName = childElement.getLocalName();
                
                Object value;
                if (hasChildElements(childElement)) {
                    value = elementToMap(childElement);
                } else {
                    value = childElement.getTextContent();
                }

                // Group elements with same name (for arrays)
                childGroups.computeIfAbsent(localName, k -> new ArrayList<>()).add(value);
            }
        }

        // Convert groups to result
        for (Map.Entry<String, List<Object>> entry : childGroups.entrySet()) {
            String name = entry.getKey();
            List<Object> values = entry.getValue();
            
            if (values.size() == 1) {
                result.put(name, values.get(0));
            } else {
                result.put(name, values);
            }
        }

        return result;
    }

    private boolean hasChildElements(Element element) {
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i).getNodeType() == Node.ELEMENT_NODE) {
                return true;
            }
        }
        return false;
    }
}
