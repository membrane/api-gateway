/*
 *  Copyright 2022 predic8 GmbH, www.predic8.com
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.predic8.membrane.core.openapi.validators;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.predic8.membrane.core.openapi.util.SchemaUtil;
import com.predic8.membrane.core.util.xml.parser.HardenedXmlParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.StringReader;
import java.util.Map;

/**
 * Converts an XML document into a Jackson {@link JsonNode} tree, guided by an
 * OpenAPI {@link Schema} so that the existing JSON-Schema validators can then
 * validate the result without modification.
 *
 * <p>Supported OpenAPI XML Object hints:
 * <ul>
 *   <li>{@code xml.name}        – overrides the element/wrapper name for a property</li>
 *   <li>{@code xml.attribute}   – maps an XML attribute to a schema property</li>
 *   <li>{@code xml.wrapped}     – signals that an array is enclosed in a wrapper element</li>
 * </ul>
 */
@SuppressWarnings("rawtypes")
public class XmlToJsonConverter {

    private static final ObjectMapper om = new ObjectMapper();

    private final OpenAPI api;

    public XmlToJsonConverter(OpenAPI api) {
        this.api = api;
    }

    /**
     * Parses {@code xmlString} and converts the root element to a {@link JsonNode}
     * according to {@code schema}.
     */
    public JsonNode convert(String xmlString, Schema schema) throws IOException, SAXException {
        return convertElement(getParse(xmlString).getDocumentElement(), resolveRef(schema));
    }

    private static Document getParse(String xmlString) {
        return HardenedXmlParser.getInstance().parse(new InputSource(new StringReader(xmlString)));
    }

    private JsonNode convertElement(Element element, Schema schema) {
        checkNoMixedContent(element);

        if (schema == null)
            return om.getNodeFactory().textNode(element.getTextContent().trim());

        schema = resolveRef(schema);
        String type = effectiveType(schema);

        if ("array".equals(type))
            return convertArrayElement(element, schema);

        if ("object".equals(type) || (type == null && schema.getProperties() != null))
            return convertObjectElement(element, schema);

        return convertPrimitive(element.getTextContent().trim(), schema);
    }

    // -----------------------------------------------------------------------
    // Object
    // -----------------------------------------------------------------------

    private ObjectNode convertObjectElement(Element element, Schema schema) {
        ObjectNode node = om.createObjectNode();

        @SuppressWarnings("unchecked")
        Map<String, Schema> properties = (Map<String, Schema>) schema.getProperties();
        if (properties == null)
            return node;

        for (Map.Entry<String, Schema> entry : properties.entrySet()) {
            String propName  = entry.getKey();
            Schema propSchema = resolveRef(entry.getValue());

            String xmlName = xmlNameOf(propName, propSchema);
            boolean isAttr = isXmlAttribute(propSchema);

            if (isAttr) {
                // Map XML attribute → JSON property
                String attrValue = element.getAttribute(xmlName);
                if (!attrValue.isEmpty())
                    node.set(propName, convertPrimitive(attrValue, propSchema));

            } else if ("array".equals(effectiveType(propSchema))) {
                node.set(propName, convertArrayProperty(element, propName, propSchema));

            } else {
                // Regular single-valued child element
                Element child = singleChildByName(element, xmlName);
                if (child != null)
                    node.set(propName, convertElement(child, propSchema));
            }
        }
        return node;
    }

    // -----------------------------------------------------------------------
    // Array
    // -----------------------------------------------------------------------

    /**
     * Handles an array schema that is the *root* of the conversion (rare but possible).
     */
    private ArrayNode convertArrayElement(Element element, Schema schema) {
        ArrayNode arrayNode = om.createArrayNode();
        Schema itemSchema = resolveRef(schema.getItems());
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element child)
                arrayNode.add(convertElement(child, itemSchema));
        }
        return arrayNode;
    }

    /**
     * Handles an array *property* inside an object, respecting {@code xml.wrapped}.
     */
    private ArrayNode convertArrayProperty(Element parentElement, String propName, Schema propSchema) {
        ArrayNode arrayNode = om.createArrayNode();

        Schema itemSchema  = resolveRef(propSchema.getItems());
        boolean wrapped    = isXmlWrapped(propSchema);
        String  wrapperName = xmlNameOf(propName, propSchema);          // name of wrapper / sibling element
        String  itemName    = xmlNameOf(propName, itemSchema);          // name of each item element

        if (wrapped) {
            // Items live inside a single wrapper element  <wrapperName><itemName/><itemName/>…</wrapperName>
            Element wrapperEl = singleChildByName(parentElement, wrapperName);
            if (wrapperEl == null)
                return arrayNode;
            NodeList children = wrapperEl.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                if (children.item(i) instanceof Element child)
                    arrayNode.add(convertElement(child, itemSchema));
            }
        } else {
            // Items are direct children of the parent element with the item's xml.name
            NodeList children = parentElement.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                if (children.item(i) instanceof Element child
                    && child.getTagName().equals(itemName))
                    arrayNode.add(convertElement(child, itemSchema));
            }
        }
        return arrayNode;
    }

    // -----------------------------------------------------------------------
    // Primitives
    // -----------------------------------------------------------------------

    private JsonNode convertPrimitive(String text, Schema schema) {
        String type = schema != null ? effectiveType(schema) : null;
        if (type == null)
            return om.getNodeFactory().textNode(text);

        return switch (type) {
            case "integer" -> {
                try { yield om.getNodeFactory().numberNode(Long.parseLong(text.trim())); }
                catch (NumberFormatException e) { yield om.getNodeFactory().textNode(text); }
            }
            case "number" -> {
                try { yield om.getNodeFactory().numberNode(Double.parseDouble(text.trim())); }
                catch (NumberFormatException e) { yield om.getNodeFactory().textNode(text); }
            }
            case "boolean" -> {
                // XML Schema xs:boolean has the lexical space {true, false, 1, 0} and is
                // case-sensitive, so only these exact literals are valid (e.g. "True" is not).
                String t = text.trim();
                if ("true".equals(t) || "1".equals(t))  yield om.getNodeFactory().booleanNode(true);
                if ("false".equals(t) || "0".equals(t)) yield om.getNodeFactory().booleanNode(false);
                yield om.getNodeFactory().textNode(text);   // invalid literal → let the validator report it
            }
            default        -> om.getNodeFactory().textNode(text);
        };
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Returns the effective type string for a schema, handling both OAS 3.0
     * ({@code schema.getType()}) and OAS 3.1 ({@code schema.getTypes()}).
     * If multiple types are declared the first non-"null" type wins.
     */
    @SuppressWarnings("unchecked")
    private static String effectiveType(Schema schema) {
        String type = schema.getType();
        if (type != null)
            return type;
        // OAS 3.1: type is stored as a Set<String>
        var types = schema.getTypes();
        if (types == null || types.isEmpty())
            return null;
        //noinspection unchecked
        return ((java.util.Set<Object>) types).stream()
                .map(Object::toString)
                .filter(t -> !"null".equals(t))
                .findFirst().orElse(null);
    }

    /** Resolves a {@code $ref} to the actual schema; returns {@code schema} unchanged if no ref. */
    private Schema resolveRef(Schema schema) {
        if (schema == null)
            return null;
        if (schema.get$ref() == null)
            return schema;
        return SchemaUtil.getSchemaFromRef(api, schema.get$ref());
    }

    /**
     * Returns the (possibly qualified) XML name for a property.
     *
     * <p>The local name is taken from {@code xml.name}, falling back to the property name.
     * If {@code xml.prefix} is set, the result is {@code prefix:localName} so it matches the
     * qualified tag/attribute name produced by the namespace-aware parser. The prefix may also
     * be baked directly into {@code xml.name} (e.g. {@code "ns:id"}) without setting {@code xml.prefix}.
     */
    private static String xmlNameOf(String propertyName, Schema schema) {
        if (schema == null || schema.getXml() == null)
            return propertyName;

        String localName = schema.getXml().getName() != null ? schema.getXml().getName() : propertyName;
        String prefix = schema.getXml().getPrefix();
        if (prefix != null && !prefix.isEmpty())
            return prefix + ":" + localName;
        return localName;
    }

    private static boolean isXmlAttribute(Schema schema) {
        return schema != null
            && schema.getXml() != null
            && Boolean.TRUE.equals(schema.getXml().getAttribute());
    }

    private static boolean isXmlWrapped(Schema schema) {
        return schema != null
            && schema.getXml() != null
            && Boolean.TRUE.equals(schema.getXml().getWrapped());
    }

    /**
     * Rejects mixed content: an element that has child elements <em>and</em> non-whitespace
     * text directly below it. Whitespace-only text between elements (e.g. indentation in a
     * pretty-printed document) is not considered mixed content and is allowed.
     */
    private static void checkNoMixedContent(Element element) {
        boolean hasChildElements = false;
        boolean hasSignificantText = false;

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            short type = n.getNodeType();
            if (type == Node.ELEMENT_NODE) {
                hasChildElements = true;
            } else if (type == Node.TEXT_NODE || type == Node.CDATA_SECTION_NODE) {
                String text = n.getNodeValue();
                if (text != null && !text.trim().isEmpty())
                    hasSignificantText = true;
            }
        }

        if (hasChildElements && hasSignificantText)
            throw new MixedContentException(element.getTagName());
    }

    /**
     * Returns the single direct child element with the given tag name, or {@code null} if there
     * is none. If the element occurs more than once - where the schema expects a single value -
     * a {@link MultipleElementsException} is thrown rather than silently keeping the first.
     */
    private static Element singleChildByName(Element parent, String name) {
        Element found = null;
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n instanceof Element el && el.getTagName().equals(name)) {
                if (found != null)
                    throw new MultipleElementsException(name);
                found = el;
            }
        }
        return found;
    }
}
