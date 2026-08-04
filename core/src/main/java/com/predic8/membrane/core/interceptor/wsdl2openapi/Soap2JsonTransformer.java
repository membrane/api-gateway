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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.models.media.*;
import org.w3c.dom.*;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.predic8.membrane.core.util.xml.parser.HardenedXmlParser.getInstance;
import static org.w3c.dom.Node.ELEMENT_NODE;

/**
 * Transforms SOAP XML response to JSON.
 * XML attributes are mapped to "@"-prefixed properties; xmlns declarations and xml: namespace
 * attributes (e.g. xml:lang) are excluded.
 */
public class Soap2JsonTransformer {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public String transform(String soapXml) throws Exception {
        return transform(soapXml, null);
    }

    /**
     * Transforms the SOAP response to JSON, using {@code responseSchema} to produce
     * properly typed values (numbers, booleans) rather than always strings.
     * Pass {@code null} to fall back to all-string behaviour.
     */
    public String transform(String soapXml, Schema<?> responseSchema) throws Exception {
        Document doc = getInstance().parse(new InputSource(new StringReader(soapXml)));

        Element body = getSoapBody(doc);
        if (body == null) {
            throw new IllegalArgumentException("No SOAP Body found in response");
        }

        Element responseElement = getFirstChildElement(body);
        if (responseElement == null) {
            throw new IllegalArgumentException("No response element found in SOAP Body");
        }

        if ("Fault".equals(responseElement.getLocalName())) {
            throw buildFaultException(responseElement);
        }

        Map<String, Object> jsonMap = elementToMap(responseElement, responseSchema);
        return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(jsonMap);
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
            if (node.getNodeType() == ELEMENT_NODE) {
                return (Element) node;
            }
        }
        return null;
    }

    // Used by fault detail extraction (no schema, all strings)
    private Map<String, Object> elementToMap(Element element) {
        return elementToMap(element, null);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> elementToMap(Element element, Schema<?> schema) {
        Map<String, Schema<?>> properties;
        if (schema instanceof ObjectSchema os && os.getProperties() != null) {
            properties = (Map<String, Schema<?>>) (Map<?, ?>) os.getProperties();
        } else {
            properties = Map.of();
        }

        var result = new LinkedHashMap<String, Object>();
        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attr = attributes.item(i);
            String ns = attr.getNamespaceURI();
            if (XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(ns) || XMLConstants.XML_NS_URI.equals(ns)) {
                continue;
            }
            result.put("@" + attr.getLocalName(), convertLeaf(attr.getNodeValue(), properties.get("@" + attr.getLocalName())));
        }

        var childGroups = new LinkedHashMap<String, List<Object>>();
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() != ELEMENT_NODE) continue;
            Element childElement = (Element) node;
            String localName = childElement.getLocalName();

            // ArraySchema wraps the per-item schema; unwrap it so we type individual instances correctly
            Schema<?> childSchema = properties.get(localName);
            Schema<?> effectiveSchema = childSchema instanceof ArraySchema as ? as.getItems() : childSchema;

            Object value;
            if (hasChildElements(childElement)) {
                value = elementToMap(childElement, effectiveSchema);
            } else {
                value = convertLeaf(childElement.getTextContent(), effectiveSchema);
            }

            childGroups.computeIfAbsent(localName, k -> new ArrayList<>()).add(value);
        }

        for (var entry : childGroups.entrySet()) {
            var values = entry.getValue();
            result.put(entry.getKey(), values.size() == 1 ? values.getFirst() : values);
        }
        return result;
    }

    private Object convertLeaf(String text, Schema<?> schema) {
        if (schema instanceof IntegerSchema) {
            try { return Long.parseLong(text.trim()); } catch (NumberFormatException e) { return text; }
        }
        if (schema instanceof NumberSchema) {
            try { return Double.parseDouble(text.trim()); } catch (NumberFormatException e) { return text; }
        }
        if (schema instanceof BooleanSchema) {
            String v = text.trim();
            return "true".equalsIgnoreCase(v) || "1".equals(v);
        }
        return text;
    }

    private SoapFaultException buildFaultException(Element fault) {
        String ns = fault.getNamespaceURI();
        if ("http://www.w3.org/2003/05/soap-envelope".equals(ns)) {
            return extractSoap12Fault(fault);
        }
        return extractSoap11Fault(fault);
    }

    private SoapFaultException extractSoap11Fault(Element fault) {
        String code = childText(fault, "faultcode");
        Element detailEl = childElement(fault, "detail");
        return new SoapFaultException(
                code,
                childText(fault, "faultstring"),
                (code.endsWith(":Client") || "Client".equals(code)) ? 400 : 500,
                detailEl != null ? elementToMap(detailEl) : null
        );
    }

    private SoapFaultException extractSoap12Fault(Element fault) {
        Element codeEl = childElement(fault, "Code");
        String code = codeEl != null ? childText(codeEl, "Value") : "";
        Element reasonEl = childElement(fault, "Reason");
        Element detailEl = childElement(fault, "Detail");
        return new SoapFaultException(
                code,
                reasonEl != null ? childText(reasonEl, "Text") : "",
                (code.endsWith(":Sender") || "Sender".equals(code)) ? 400 : 500,
                detailEl != null ? elementToMap(detailEl) : null
        );
    }

    private String childText(Element parent, String localName) {
        Element child = childElement(parent, localName);
        return child != null ? child.getTextContent().trim() : "";
    }

    private Element childElement(Element parent, String localName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == ELEMENT_NODE && localName.equals(node.getLocalName())) {
                return (Element) node;
            }
        }
        return null;
    }

    private boolean hasChildElements(Element element) {
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i).getNodeType() == ELEMENT_NODE) {
                return true;
            }
        }
        return false;
    }
}
