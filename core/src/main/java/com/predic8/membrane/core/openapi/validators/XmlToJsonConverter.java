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
import io.swagger.v3.oas.models.media.XML;
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
 *   <li>{@code xml.prefix}      – qualifies the name with a namespace prefix ({@code prefix:name})</li>
 *   <li>{@code xml.namespace}   – matches elements/attributes by namespace URI + local name,
 *                                 independent of the prefix used in the document</li>
 *   <li>{@code xml.attribute}   – maps an XML attribute to a schema property</li>
 *   <li>{@code xml.wrapped}     – signals that an array is enclosed in a wrapper element</li>
 * </ul>
 *
 * <p>When {@code xml.namespace} is present, matching is namespace-aware: the namespace URI and
 * local name decide the match and the document's prefix is irrelevant. When it is absent, names
 * are matched literally against the qualified tag name (optionally built from {@code xml.prefix}),
 * which keeps the prefix-sensitive behaviour for specs that do not declare namespace URIs.
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

            XmlName xmlName = xmlNameOf(propName, propSchema);
            boolean isAttr = isXmlAttribute(propSchema);

            if (isAttr) {
                // Map XML attribute → JSON property
                String attrValue = attributeValue(element, xmlName);
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
        XmlName wrapperName = xmlNameOf(propName, propSchema);          // name of wrapper / sibling element
        XmlName itemName    = xmlNameOf(propName, itemSchema);          // name of each item element

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
                    && itemName.matches(child))
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
     * Returns the {@link XmlName} a property is matched by.
     *
     * <p>The local name is taken from {@code xml.name}, falling back to the property name.
     * If {@code xml.namespace} is set, matching is namespace-aware (URI + local name) and the
     * document's prefix is ignored. Otherwise the result carries a literal qualified name: if
     * {@code xml.prefix} is set it is {@code prefix:localName}, matching the qualified tag/attribute
     * name produced by the namespace-aware parser. The prefix may also be baked directly into
     * {@code xml.name} (e.g. {@code "ns:id"}) without setting {@code xml.prefix}.
     */
    private static XmlName xmlNameOf(String propertyName, Schema schema) {
        XML xml = (schema == null) ? null : schema.getXml();

        String localName = (xml != null && xml.getName() != null) ? xml.getName() : propertyName;
        String prefix    = (xml != null) ? xml.getPrefix()    : null;
        String namespace = (xml != null) ? xml.getNamespace() : null;

        if (namespace != null && !namespace.isEmpty())
            // Namespace-aware: ignore the document prefix, match URI + local part.
            return new XmlName(namespace, stripPrefix(localName), null);

        // Backward-compatible literal matching on the qualified tag name.
        String qualified = (prefix != null && !prefix.isEmpty()) ? prefix + ":" + localName : localName;
        return new XmlName(null, stripPrefix(localName), qualified);
    }

    /** Drops a leading {@code prefix:} from a name, leaving the local part. */
    private static String stripPrefix(String name) {
        int i = name.indexOf(':');
        return (i >= 0) ? name.substring(i + 1) : name;
    }

    /** Reads an attribute value, namespace-aware when the {@link XmlName} carries a namespace URI. */
    private static String attributeValue(Element element, XmlName name) {
        return (name.namespaceURI() != null)
            ? element.getAttributeNS(name.namespaceURI(), name.localName())
            : element.getAttribute(name.qualifiedName());
    }

    /**
     * The expected XML identity of a property. When {@code namespaceURI} is set, matching is
     * namespace-aware (URI + local name) and the document's prefix is irrelevant; otherwise the
     * match falls back to the literal qualified tag name for backward compatibility.
     */
    private record XmlName(String namespaceURI, String localName, String qualifiedName) {

        boolean matches(Element el) {
            if (namespaceURI != null)
                return namespaceURI.equals(el.getNamespaceURI())
                    && localName.equals(localNameOf(el));
            return qualifiedName.equals(el.getTagName());
        }

        private static String localNameOf(Element el) {
            return el.getLocalName() != null ? el.getLocalName() : el.getTagName();
        }
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
    private static Element singleChildByName(Element parent, XmlName name) {
        Element found = null;
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n instanceof Element el && name.matches(el)) {
                if (found != null)
                    throw new MultipleElementsException(name.localName());
                found = el;
            }
        }
        return found;
    }
}
