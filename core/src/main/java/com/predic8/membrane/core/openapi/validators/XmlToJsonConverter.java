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
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
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

    private static final DocumentBuilderFactory FACTORY;

    static {
        FACTORY = DocumentBuilderFactory.newInstance();
        FACTORY.setNamespaceAware(false);
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final OpenAPI api;

    public XmlToJsonConverter(OpenAPI api) {
        this.api = api;
    }

    // -----------------------------------------------------------------------
    // Public entry point
    // -----------------------------------------------------------------------

    /**
     * Parses {@code xmlString} and converts the root element to a {@link JsonNode}
     * according to {@code schema}.
     */
    public JsonNode convert(String xmlString, Schema schema) throws IOException, SAXException {
        Document doc;
        try {
            DocumentBuilder builder = FACTORY.newDocumentBuilder();
            doc = builder.parse(new InputSource(new StringReader(xmlString)));
        } catch (ParserConfigurationException e) {
            throw new IOException("Cannot create XML parser", e);
        }
        return convertElement(doc.getDocumentElement(), resolveRef(schema));
    }

    // -----------------------------------------------------------------------
    // Core conversion
    // -----------------------------------------------------------------------

    private JsonNode convertElement(Element element, Schema schema) {
        if (schema == null)
            return MAPPER.getNodeFactory().textNode(element.getTextContent().trim());

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
        ObjectNode node = MAPPER.createObjectNode();

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
                // Regular child element
                Element child = firstChildByName(element, xmlName);
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
        ArrayNode arrayNode = MAPPER.createArrayNode();
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
        ArrayNode arrayNode = MAPPER.createArrayNode();

        Schema itemSchema  = resolveRef(propSchema.getItems());
        boolean wrapped    = isXmlWrapped(propSchema);
        String  wrapperName = xmlNameOf(propName, propSchema);          // name of wrapper / sibling element
        String  itemName    = xmlNameOf(propName, itemSchema);          // name of each item element

        if (wrapped) {
            // Items live inside a wrapper element  <wrapperName><itemName/><itemName/>…</wrapperName>
            Element wrapperEl = firstChildByName(parentElement, wrapperName);
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
            return MAPPER.getNodeFactory().textNode(text);

        return switch (type) {
            case "integer" -> {
                try { yield MAPPER.getNodeFactory().numberNode(Long.parseLong(text.trim())); }
                catch (NumberFormatException e) { yield MAPPER.getNodeFactory().textNode(text); }
            }
            case "number" -> {
                try { yield MAPPER.getNodeFactory().numberNode(Double.parseDouble(text.trim())); }
                catch (NumberFormatException e) { yield MAPPER.getNodeFactory().textNode(text); }
            }
            case "boolean" -> MAPPER.getNodeFactory().booleanNode(Boolean.parseBoolean(text.trim()));
            default        -> MAPPER.getNodeFactory().textNode(text);
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

    /** Returns the XML element name for a property, falling back to the property name. */
    private static String xmlNameOf(String propertyName, Schema schema) {
        if (schema != null && schema.getXml() != null && schema.getXml().getName() != null)
            return schema.getXml().getName();
        return propertyName;
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

    /** Returns the first direct child element with the given tag name, or {@code null}. */
    private static Element firstChildByName(Element parent, String name) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n instanceof Element el && el.getTagName().equals(name))
                return el;
        }
        return null;
    }
}
