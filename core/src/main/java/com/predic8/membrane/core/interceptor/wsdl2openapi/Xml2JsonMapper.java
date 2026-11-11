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

import io.swagger.v3.oas.models.media.*;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import javax.xml.XMLConstants;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.predic8.membrane.core.interceptor.wsdl2openapi.XsdDomUtil.*;

/**
 * Maps an XML element to JSON, typed by the schema {@link XsdToSchema} derived from its XSD
 * declaration.
 * XML attributes are mapped to "@"-prefixed properties; xmlns declarations and xml: namespace
 * attributes (e.g. xml:lang) are excluded.
 * An element marked {@code xsi:nil="true"} becomes JSON {@code null}, matching the
 * {@code nullable} that {@code XsdToSchema} derives from {@code nillable="true"}.
 * An element that has attributes but no child elements becomes an object carrying those attributes
 * plus its own text value under {@code $value}; so does an element whose schema declares
 * {@code $value}, even when none of its optional attributes is present.
 * A root element that is itself a scalar (no child elements, and the schema names an XSD scalar
 * type rather than an object) is emitted as a bare JSON value, not an empty object; a root of an
 * {@code xsd:simpleContent} type keeps its typed text value the same way a child element does.
 *
 * <p>Stateless apart from the components it resolves references against, so one instance can
 * serve concurrent requests as long as nobody modifies those schemas.
 */
class Xml2JsonMapper {

    /**
     * The named types the schemas refer to, keyed by component name — the same map
     * {@link XsdToSchema#getComponents()} fills. Empty when the caller has no schemas to resolve
     * against, in which case a reference is simply left as it is.
     */
    private final Map<String, Schema<?>> components;

    Xml2JsonMapper(Map<String, Schema<?>> components) {
        this.components = components;
    }

    /**
     * The JSON value of a root element: {@code null} if it is nil, its leaf value if it is a scalar
     * or simpleContent root, otherwise a map. {@code schema} may be {@code null}, which converts
     * the element structurally with every scalar as a string.
     */
    Object toJson(Element element, Schema<?> schema) {
        if (isNil(element)) return null;
        if (isScalarRoot(element, schema) || isSimpleContentRoot(element, schema)) return leafValue(element, schema);
        return elementToMap(element, schema);
    }

    /** The schema a reference points at — see {@link XsdDomUtil#dereference}. */
    private Schema<?> resolve(Schema<?> schema) {
        return dereference(components, schema);
    }

    /**
     * Whether the root response element must be emitted as a bare JSON scalar rather than an
     * object: it has no child elements, and the schema published for it names an XSD scalar type
     * (string, integer, ...) rather than an object — an empty {@code complexType} keeps its
     * {@code {}} shape, since the schema there still says "object".
     */
    private boolean isScalarRoot(Element element, Schema<?> schema) {
        if (hasChildElements(element)) return false;
        Schema<?> resolved = resolve(schema);
        if (resolved == null) return false;
        String type = resolved.getType();
        return type != null && !"object".equals(type) && !"array".equals(type);
    }

    /**
     * Whether the root element is of an {@code xsd:simpleContent} type: it has no child elements,
     * and its schema declares the text value under {@code $value}. Its schema says "object", so
     * {@link #isScalarRoot} rejects it, but {@link #elementToMap} would drop its text — it gets
     * the same {@link #leafValue} treatment as a simpleContent child element.
     */
    private boolean isSimpleContentRoot(Element element, Schema<?> schema) {
        return !hasChildElements(element) && propertiesOf(resolve(schema)).containsKey(VALUE_KEY);
    }

    Map<String, Object> elementToMap(Element element, Schema<?> schema) {
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
     * type with attributes. When the schema declares {@code $value}, the element is that object
     * even if none of its optional attributes is present, so that the JSON keeps the declared shape.
     * Otherwise, without attributes, the value stands on its own, as a plain scalar.
     */
    private Object leafValue(Element element, Schema<?> schema) {
        Schema<?> resolved = resolve(schema);
        Map<String, Schema<?>> properties = propertiesOf(resolved);

        var attributes = new LinkedHashMap<String, Object>();
        putAttributes(element, properties, attributes);

        Schema<?> declaredValueSchema = resolve(properties.get(VALUE_KEY));
        Object text = convertLeaf(element.getTextContent(),
                declaredValueSchema != null ? declaredValueSchema : resolved);

        if (attributes.isEmpty() && declaredValueSchema == null) return text;
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

    private static Object convertLeaf(String text, Schema<?> schema) {
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

    private static boolean hasChildElements(Element element) {
        return !childElements(element).isEmpty();
    }
}
