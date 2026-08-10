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

import static com.predic8.membrane.annot.Constants.SOAP11_NS;
import static com.predic8.membrane.annot.Constants.SOAP12_NS;
import static com.predic8.membrane.core.interceptor.wsdl2openapi.XsdDomUtil.attributeKey;
import static com.predic8.membrane.core.interceptor.wsdl2openapi.XsdDomUtil.childElements;
import static com.predic8.membrane.core.util.xml.parser.HardenedXmlParser.getInstance;

/**
 * Transforms SOAP XML response to JSON.
 * XML attributes are mapped to "@"-prefixed properties; xmlns declarations and xml: namespace
 * attributes (e.g. xml:lang) are excluded.
 */
public class Soap2JsonTransformer {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Schema-less fallback: converts the SOAP response structurally, with all scalar
     * values produced as JSON strings. Use when no response schema is available,
     * e.g. for untyped/{@code xsd:any} elements.
     */
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
        Element body = bodyInNamespace(doc, SOAP11_NS);
        return body != null ? body : bodyInNamespace(doc, SOAP12_NS);
    }

    private static Element bodyInNamespace(Document doc, String soapNamespace) {
        NodeList bodies = doc.getElementsByTagNameNS(soapNamespace, "Body");
        return bodies.getLength() > 0 ? (Element) bodies.item(0) : null;
    }

    private Element getFirstChildElement(Element parent) {
        var children = childElements(parent);
        return children.isEmpty() ? null : children.getFirst();
    }

    // Used by fault detail extraction (no schema, all strings)
    private Map<String, Object> elementToMap(Element element) {
        return elementToMap(element, null);
    }

    private Map<String, Object> elementToMap(Element element, Schema<?> schema) {
        Map<String, Schema<?>> properties = propertiesOf(schema);

        var result = new LinkedHashMap<String, Object>();
        putAttributes(element, properties, result);

        var childGroups = new LinkedHashMap<String, ChildGroup>();
        for (Element childElement : childElements(element)) {
            ResolvedChild child = resolveChild(properties, childElement);
            // ArraySchema wraps the per-item schema; unwrap it so we type individual instances correctly
            Schema<?> effectiveSchema = child.schema() instanceof ArraySchema as ? as.getItems() : child.schema();

            Object value = hasChildElements(childElement)
                    ? elementToMap(childElement, effectiveSchema)
                    : convertLeaf(childElement.getTextContent(), effectiveSchema);

            childGroups.computeIfAbsent(child.key(), k -> new ChildGroup(child.schema(), new ArrayList<>()))
                    .values().add(value);
        }

        collapseGroups(childGroups, result);
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Schema<?>> propertiesOf(Schema<?> schema) {
        if (schema instanceof ObjectSchema os && os.getProperties() != null) {
            return (Map<String, Schema<?>>) (Map<?, ?>) os.getProperties();
        }
        return Map.of();
    }

    /** Copies the element's attributes into {@code result} as {@code @}-prefixed properties. */
    private void putAttributes(Element element, Map<String, Schema<?>> properties, Map<String, Object> result) {
        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attr = attributes.item(i);
            String ns = attr.getNamespaceURI();
            if (XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(ns) || XMLConstants.XML_NS_URI.equals(ns)) {
                continue;
            }
            String key = attributeKey(attr.getLocalName());
            result.put(key, convertLeaf(attr.getNodeValue(), properties.get(key)));
        }
    }

    /** A group stays a JSON array if its schema says so or it holds more than one value. */
    private static void collapseGroups(Map<String, ChildGroup> childGroups, Map<String, Object> result) {
        childGroups.forEach((name, group) -> result.put(name,
                group.schema() instanceof ArraySchema || group.values().size() > 1
                        ? group.values()
                        : group.values().getFirst()));
    }

    /** The values collected under one property key, together with the schema they were typed with. */
    private record ChildGroup(Schema<?> schema, List<Object> values) {}

    /** The schema a child element was typed with, and the property key it was found under. */
    private record ResolvedChild(String key, Schema<?> schema) {}

    /**
     * Resolves a child element against the parent's properties. The namespace-qualified key that
     * {@code XsdToSchema.addChoiceFields} uses for same-local-name alternatives is preferred, so
     * that alternatives from different namespaces stay distinct properties and the emitted JSON
     * matches the published schema — {@code Json2SoapTransformer} strips such keys back to the XML
     * local name on the way in. Everything else keeps its plain local name.
     */
    private static ResolvedChild resolveChild(Map<String, Schema<?>> properties, Element childElement) {
        String localName = childElement.getLocalName();
        String namespaceURI = childElement.getNamespaceURI();
        if (namespaceURI != null) {
            String qualified = XsdDomUtil.qualifiedKey(namespaceURI, localName);
            Schema<?> qualifiedSchema = properties.get(qualified);
            if (qualifiedSchema != null) return new ResolvedChild(qualified, qualifiedSchema);
        }
        return new ResolvedChild(localName, properties.get(localName));
    }

    private Object convertLeaf(String text, Schema<?> schema) {
        return switch (schema) {
            case IntegerSchema ignored -> parseLongOrText(text);
            case NumberSchema ignored -> parseDoubleOrText(text);
            case BooleanSchema ignored -> isTrue(text);
            case null, default -> text;
        };
    }

    private static Object parseLongOrText(String text) {
        try { return Long.parseLong(text.trim()); } catch (NumberFormatException e) { return text; }
    }

    private static Object parseDoubleOrText(String text) {
        try { return Double.parseDouble(text.trim()); } catch (NumberFormatException e) { return text; }
    }

    private static boolean isTrue(String text) {
        String value = text.trim();
        return "true".equalsIgnoreCase(value) || "1".equals(value);
    }

    private SoapFaultException buildFaultException(Element fault) {
        String ns = fault.getNamespaceURI();
        if (SOAP12_NS.equals(ns)) {
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
        for (Element el : childElements(parent)) {
            if (localName.equals(el.getLocalName())) return el;
        }
        return null;
    }

    private boolean hasChildElements(Element element) {
        return !childElements(element).isEmpty();
    }
}
