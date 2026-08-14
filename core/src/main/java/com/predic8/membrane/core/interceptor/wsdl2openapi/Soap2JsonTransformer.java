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
import static com.predic8.membrane.core.interceptor.wsdl2openapi.XsdDomUtil.*;
import static com.predic8.membrane.core.util.xml.parser.HardenedXmlParser.getInstance;

/**
 * Transforms SOAP XML response to JSON.
 * XML attributes are mapped to "@"-prefixed properties; xmlns declarations and xml: namespace
 * attributes (e.g. xml:lang) are excluded.
 * An element marked {@code xsi:nil="true"} becomes JSON {@code null}, matching the
 * {@code nullable} that {@code XsdToSchema} derives from {@code nillable="true"}.
 * An element that has attributes but no child elements becomes an object carrying those attributes
 * plus its own text value under {@code $value}.
 */
public class Soap2JsonTransformer {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * The named types the schemas refer to, keyed by component name — the same map
     * {@link XsdToSchema#getComponents()} fills. Empty when the caller has no schemas to resolve
     * against, in which case a reference is simply left as it is.
     */
    private final Map<String, Schema<?>> components;

    public Soap2JsonTransformer() {
        this(Map.of());
    }

    public Soap2JsonTransformer(Map<String, Schema<?>> components) {
        this.components = components;
    }

    /** The schema a reference points at — see {@link XsdDomUtil#dereference}. */
    private Schema<?> resolve(Schema<?> schema) {
        return dereference(components, schema);
    }

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
        return transform(soapXml, responseSchema, null);
    }

    /**
     * Transforms the SOAP response to JSON. {@code faultDetailSchema} types the content of a SOAP
     * {@code <detail>} element the same way {@code responseSchema} types a successful response: its
     * properties are the operation's declared faults, keyed by fault element local name.
     */
    public String transform(String soapXml, Schema<?> responseSchema, Schema<?> faultDetailSchema) throws Exception {
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
            throw buildFaultException(responseElement, faultDetailSchema);
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
        Map<String, Schema<?>> properties = propertiesOf(resolve(schema));

        var result = new LinkedHashMap<String, Object>();
        putAttributes(element, properties, result);

        var childGroups = new LinkedHashMap<String, ChildGroup>();
        for (Element childElement : childElements(element)) {
            ResolvedChild child = resolveChild(properties, childElement);
            Schema<?> childSchema = resolve(child.schema());
            // ArraySchema wraps the per-item schema; unwrap it so we type individual instances correctly
            Schema<?> effectiveSchema = resolve(childSchema instanceof ArraySchema as ? as.getItems() : childSchema);

            Object value = childValue(childElement, effectiveSchema);

            childGroups.computeIfAbsent(child.key(), k -> new ChildGroup(childSchema, new ArrayList<>()))
                    .values().add(value);
        }

        collapseGroups(childGroups, result);
        return result;
    }

    /** The JSON value one child element contributes: null if nil, an object if compound, else its text. */
    private Object childValue(Element element, Schema<?> schema) {
        if (isNil(element)) return null;
        if (hasChildElements(element)) return elementToMap(element, schema);
        return leafValue(element, schema);
    }

    /**
     * A leaf element's text value. An element carrying attributes cannot be represented by that
     * value alone, so it becomes an object holding the attributes plus the value under
     * {@code $value} — the shape {@code XsdToSchema} publishes for an {@code xsd:simpleContent}
     * type. Without attributes the value stands on its own, as a plain scalar.
     */
    private Object leafValue(Element element, Schema<?> schema) {
        Schema<?> resolved = resolve(schema);
        Map<String, Schema<?>> properties = propertiesOf(resolved);

        var attributes = new LinkedHashMap<String, Object>();
        putAttributes(element, properties, attributes);

        Schema<?> declaredValueSchema = resolve(properties.get(VALUE_KEY));
        Object text = convertLeaf(element.getTextContent(),
                declaredValueSchema != null ? declaredValueSchema : resolved);

        if (attributes.isEmpty()) return text;
        attributes.put(VALUE_KEY, text);
        return attributes;
    }

    /**
     * The child schemas of {@code schema}, keyed by property name, or an empty map if it declares
     * none. Any schema carrying properties qualifies — not only {@link ObjectSchema} — because an
     * object arriving as a plain {@code Schema} with {@code type: "object"}, as a parsed OpenAPI
     * document produces, describes its children just as well. Narrowing to {@code ObjectSchema}
     * here would silently drop value typing and arrayness for every child of such a parent.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Schema<?>> propertiesOf(Schema<?> schema) {
        if (schema == null || schema.getProperties() == null) return Map.of();
        return (Map<String, Schema<?>>) (Map<?, ?>) schema.getProperties();
    }

    /**
     * Copies the element's attributes into {@code result} as {@code @}-prefixed properties.
     * {@code xsi:nil} is left out: it is carried by the JSON value being {@code null}, so exposing
     * it as a property too would state the same thing twice.
     */
    private void putAttributes(Element element, Map<String, Schema<?>> properties, Map<String, Object> result) {
        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attr = attributes.item(i);
            String ns = attr.getNamespaceURI();
            if (XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(ns) || XMLConstants.XML_NS_URI.equals(ns)) {
                continue;
            }
            if (XSI_NS.equals(ns) && NIL_ATTRIBUTE.equals(attr.getLocalName())) {
                continue;
            }
            String key = attributeKey(attr.getLocalName());
            result.put(key, convertLeaf(attr.getNodeValue(), resolve(properties.get(key))));
        }
    }

    /**
     * Whether the element declares itself as carrying no value via {@code xsi:nil}. A nil element
     * must be empty, so this is checked before any text content or children are looked at.
     */
    private static boolean isNil(Element element) {
        return isTrue(element.getAttributeNS(XSI_NS, NIL_ATTRIBUTE));
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

    private SoapFaultException buildFaultException(Element fault, Schema<?> faultDetailSchema) {
        String ns = fault.getNamespaceURI();
        if (SOAP12_NS.equals(ns)) {
            return extractSoap12Fault(fault, faultDetailSchema);
        }
        return extractSoap11Fault(fault, faultDetailSchema);
    }

    private SoapFaultException extractSoap11Fault(Element fault, Schema<?> faultDetailSchema) {
        return new SoapFaultException(
                childText(fault, "faultcode"),
                childText(fault, "faultstring"),
                detailMap(childElement(fault, "detail"), faultDetailSchema)
        );
    }

    private SoapFaultException extractSoap12Fault(Element fault, Schema<?> faultDetailSchema) {
        Element codeEl = childElement(fault, "Code");
        Element reasonEl = childElement(fault, "Reason");
        return new SoapFaultException(
                codeEl != null ? childText(codeEl, "Value") : "",
                reasonEl != null ? childText(reasonEl, "Text") : "",
                detailMap(childElement(fault, "Detail"), faultDetailSchema)
        );
    }

    /**
     * The SOAP {@code <detail>} content as a map keyed by the local name of each fault element it
     * wraps, or null if the fault carries no detail. {@code faultDetailSchema} types the values;
     * without it, every scalar comes out as a string.
     */
    private Map<String, Object> detailMap(Element detailElement, Schema<?> faultDetailSchema) {
        return detailElement != null ? elementToMap(detailElement, faultDetailSchema) : null;
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
