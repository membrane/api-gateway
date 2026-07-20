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

import com.predic8.membrane.core.util.wsdl.parser.Definitions;
import com.predic8.membrane.core.util.wsdl.parser.Message;
import io.swagger.v3.oas.models.media.*;
import org.slf4j.*;
import org.w3c.dom.*;

import javax.xml.namespace.QName;
import java.util.*;

import static com.predic8.membrane.annot.Constants.XSD_NS;

/**
 * Converts XSD type definitions embedded in a WSDL to OpenAPI Schema objects.
 *
 * <p>Handles:
 * <ul>
 *   <li>Inline complexType and type-reference patterns</li>
 *   <li>xsd:sequence, xsd:all (treated identically)</li>
 *   <li>xsd:choice (all alternatives become optional properties)</li>
 *   <li>xsd:complexContent/xsd:extension (base type fields are inherited)</li>
 *   <li>xsd:complexContent/xsd:restriction (treated as extension for field inheritance)</li>
 *   <li>xsd:simpleContent (approximated as string)</li>
 *   <li>Named xsd:simpleType restrictions (resolved to the base primitive)</li>
 *   <li>maxOccurs="unbounded" or > 1 (produces ArraySchema)</li>
 *   <li>Cross-namespace type references (resolved via the full import graph)</li>
 * </ul>
 */
public class XsdToSchema {

    private static final Logger log = LoggerFactory.getLogger(XsdToSchema.class);

    private final Map<String, Element> schemasByNamespace;

    public XsdToSchema(Definitions definitions) {
        this.schemasByNamespace = buildSchemaMap(definitions);
    }

    /**
     * Converts the first message part of the first message in the list to an OpenAPI schema.
     * Returns an empty ObjectSchema if the list is empty or has no usable parts.
     */
    public Schema<?> convertMessageParts(List<Message> messages) {
        if (messages.isEmpty()) return new ObjectSchema();
        var parts = messages.getFirst().getParts();
        if (parts.isEmpty()) return new ObjectSchema();
        QName qname = parts.getFirst().getElementQName();
        if (qname == null) return new ObjectSchema();
        return convert(qname);
    }

    /**
     * Converts the top-level XSD element referenced by the given QName to an OpenAPI schema.
     */
    public Schema<?> convert(QName qname) {
        Element schemaRoot = schemasByNamespace.get(qname.getNamespaceURI());
        if (schemaRoot == null) return new ObjectSchema();
        Element xsdElement = findXsdChildWithName(schemaRoot, "element", qname.getLocalPart());
        if (xsdElement == null) return new ObjectSchema();
        return convertElementType(xsdElement, schemaRoot);
    }

    // --- Internal conversion ---

    /**
     * Resolves the Schema for an {@code <xsd:element>} node — its inline type
     * or the type referenced by the {@code type} attribute.
     * Does NOT apply maxOccurs wrapping; that is done by addElementField for sequence members.
     */
    private Schema<?> convertElementType(Element xsdElement, Element schemaRoot) {
        Element inlineComplexType = findXsdChild(xsdElement, "complexType");
        if (inlineComplexType != null) {
            return buildObjectSchema(inlineComplexType, schemaRoot);
        }
        Element inlineSimpleType = findXsdChild(xsdElement, "simpleType");
        if (inlineSimpleType != null) {
            return buildSimpleTypeSchema(inlineSimpleType, xsdElement, schemaRoot);
        }
        String typeAttr = xsdElement.getAttribute("type");
        if (!typeAttr.isEmpty()) {
            return resolveTypeRef(typeAttr, xsdElement, schemaRoot);
        }
        return new ObjectSchema();
    }

    private Schema<?> buildObjectSchema(Element complexTypeEl, Element schemaRoot) {
        var objectSchema = new ObjectSchema();

        Element sequence = findXsdChild(complexTypeEl, "sequence");
        if (sequence != null) {
            addContainerFields(sequence, objectSchema, schemaRoot);
            return objectSchema;
        }
        Element all = findXsdChild(complexTypeEl, "all");
        if (all != null) {
            addContainerFields(all, objectSchema, schemaRoot);
            return objectSchema;
        }
        Element complexContent = findXsdChild(complexTypeEl, "complexContent");
        if (complexContent != null) {
            Element extension = findXsdChild(complexContent, "extension");
            if (extension != null) addExtensionFields(extension, objectSchema, schemaRoot);
            Element restriction = findXsdChild(complexContent, "restriction");
            if (restriction != null) addExtensionFields(restriction, objectSchema, schemaRoot);
            return objectSchema;
        }
        // simpleContent: a complex type whose value is text — approximate as string
        if (findXsdChild(complexTypeEl, "simpleContent") != null) {
            return new StringSchema();
        }
        return objectSchema;
    }

    /**
     * Processes children of {@code <xsd:sequence>} or {@code <xsd:all>}, dispatching
     * element, choice, and nested sequence/all nodes.
     */
    private void addContainerFields(Element container, ObjectSchema schema, Element schemaRoot) {
        NodeList children = container.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (!(child instanceof Element el) || !XSD_NS.equals(el.getNamespaceURI())) continue;
            switch (el.getLocalName()) {
                case "element"          -> addElementField(el, schema, schemaRoot);
                case "choice"           -> addChoiceFields(el, schema, schemaRoot);
                case "sequence", "all"  -> addContainerFields(el, schema, schemaRoot);
                // xsd:any, xsd:group: skip — not representable in JSON
            }
        }
    }

    private void addElementField(Element el, ObjectSchema schema, Element schemaRoot) {
        String fieldName = el.getAttribute("name");
        if (fieldName.isEmpty()) return; // ref= elements: not supported

        String minOccurs = el.getAttribute("minOccurs");
        String maxOccurs = el.getAttribute("maxOccurs");

        Schema<?> fieldSchema = convertElementType(el, schemaRoot);
        if ("unbounded".equals(maxOccurs) || isMoreThanOne(maxOccurs)) {
            fieldSchema = new ArraySchema().items(fieldSchema);
        }

        schema.addProperty(fieldName, fieldSchema);
        if (!"0".equals(minOccurs)) {
            schema.addRequiredItem(fieldName);
        }
    }

    /**
     * Maps choice alternatives to optional properties. All alternatives are present
     * in the schema but none are added to {@code required}, reflecting that exactly
     * one is expected at runtime.
     */
    private void addChoiceFields(Element choice, ObjectSchema schema, Element schemaRoot) {
        NodeList children = choice.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (!(child instanceof Element el) || !XSD_NS.equals(el.getNamespaceURI())) continue;
            if ("element".equals(el.getLocalName())) {
                String fieldName = el.getAttribute("name");
                if (!fieldName.isEmpty()) {
                    schema.addProperty(fieldName, convertElementType(el, schemaRoot));
                    // intentionally not added to required
                }
            }
        }
    }

    /**
     * Handles {@code <xsd:extension>} and (for field-extraction purposes)
     * {@code <xsd:restriction>} inside {@code <xsd:complexContent>}.
     * Merges base type fields into {@code schema} first, then appends extension fields.
     */
    private void addExtensionFields(Element extension, ObjectSchema schema, Element schemaRoot) {
        String base = extension.getAttribute("base");
        if (!base.isEmpty()) {
            Schema<?> baseSchema = resolveTypeRef(base, extension, schemaRoot);
            if (baseSchema instanceof ObjectSchema baseObj && baseObj.getProperties() != null) {
                baseObj.getProperties().forEach(schema::addProperty);
                if (baseObj.getRequired() != null) {
                    baseObj.getRequired().forEach(schema::addRequiredItem);
                }
            }
        }
        Element seq = findXsdChild(extension, "sequence");
        if (seq != null) addContainerFields(seq, schema, schemaRoot);
        Element all = findXsdChild(extension, "all");
        if (all != null) addContainerFields(all, schema, schemaRoot);
    }

    private Schema<?> buildSimpleTypeSchema(Element simpleTypeEl, Element contextEl, Element schemaRoot) {
        Element restriction = findXsdChild(simpleTypeEl, "restriction");
        if (restriction == null) return new StringSchema();
        String base = restriction.getAttribute("base");
        return base.isEmpty() ? new StringSchema() : resolveTypeRef(base, restriction, schemaRoot);
    }

    /**
     * Resolves a type reference string (e.g. {@code "tns:getBankType"}, {@code "xsd:string"})
     * to an OpenAPI schema. Uses the DOM context element for prefix→URI resolution so that
     * cross-namespace references are followed correctly.
     */
    private Schema<?> resolveTypeRef(String typeRef, Element contextElement, Element currentSchemaRoot) {
        if (typeRef.isEmpty()) return new StringSchema();
        String prefix = prefix(typeRef);
        String local = localName(typeRef);

        Element targetRoot = resolveTargetSchemaRoot(prefix, contextElement, currentSchemaRoot);

        Element complexType = findXsdChildWithName(targetRoot, "complexType", local);
        if (complexType != null) {
            return buildObjectSchema(complexType, targetRoot);
        }
        Element simpleType = findXsdChildWithName(targetRoot, "simpleType", local);
        if (simpleType != null) {
            return buildSimpleTypeSchema(simpleType, contextElement, targetRoot);
        }
        return mapPrimitive(local);
    }

    /**
     * Returns the schema root element for the namespace identified by {@code prefix}
     * in the context of {@code contextElement}. Falls back to {@code currentSchemaRoot}
     * if the prefix can't be resolved or the namespace has no known schema.
     */
    private Element resolveTargetSchemaRoot(String prefix, Element contextElement, Element currentSchemaRoot) {
        if (prefix.isEmpty()) return currentSchemaRoot;
        String nsUri = contextElement.lookupNamespaceURI(prefix);
        if (nsUri == null) return currentSchemaRoot;
        return schemasByNamespace.getOrDefault(nsUri, currentSchemaRoot);
    }

    private Schema<?> mapPrimitive(String localPart) {
        return switch (localPart) {
            case "string", "anyURI", "normalizedString", "token", "language",
                 "date", "dateTime", "time", "gYear", "gMonth", "gDay",
                 "gYearMonth", "gMonthDay", "duration",
                 "hexBinary", "base64Binary", "QName", "NOTATION" -> new StringSchema();
            case "integer", "int", "long", "short", "byte",
                 "nonNegativeInteger", "positiveInteger",
                 "nonPositiveInteger", "negativeInteger",
                 "unsignedInt", "unsignedShort", "unsignedByte", "unsignedLong" -> new IntegerSchema();
            case "decimal", "float", "double" -> new NumberSchema();
            case "boolean" -> new BooleanSchema();
            default -> {
                log.debug("Unknown XSD type '{}', defaulting to string", localPart);
                yield new StringSchema();
            }
        };
    }


    /**
     * Builds a namespace → schema root element map by traversing the full import
     * graph of the WSDL. Starts from directly embedded schemas and follows
     * {@code <xsd:import>} chains transitively, so cross-namespace type references
     * in imported files are also resolved.
     */
    private static Map<String, Element> buildSchemaMap(Definitions definitions) {
        var map = new LinkedHashMap<String, Element>();
        var queue = new ArrayDeque<>(definitions.getSchemas());
        while (!queue.isEmpty()) {
            var schema = queue.poll();
            var ns = schema.getTargetNamespace();
            if (ns != null && !map.containsKey(ns)) {
                map.put(ns, schema.getSchemaElement());
            }
            for (var imp : schema.getImports()) {
                var imported = imp.getSchema();
                if (imported != null && !map.containsKey(imported.getTargetNamespace())) {
                    queue.add(imported);
                }
            }
        }
        return map;
    }

    private Element findXsdChildWithName(Element parent, String xsdLocalName, String nameAttr) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element el
                    && XSD_NS.equals(el.getNamespaceURI())
                    && xsdLocalName.equals(el.getLocalName())
                    && nameAttr.equals(el.getAttribute("name"))) {
                return el;
            }
        }
        return null;
    }

    private Element findXsdChild(Element parent, String xsdLocalName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element el
                    && XSD_NS.equals(el.getNamespaceURI())
                    && xsdLocalName.equals(el.getLocalName())) {
                return el;
            }
        }
        return null;
    }

    private String prefix(String qualifiedName) {
        int colon = qualifiedName.indexOf(':');
        return colon >= 0 ? qualifiedName.substring(0, colon) : "";
    }

    private String localName(String qualifiedName) {
        int colon = qualifiedName.indexOf(':');
        return colon >= 0 ? qualifiedName.substring(colon + 1) : qualifiedName;
    }

    private boolean isMoreThanOne(String maxOccurs) {
        if (maxOccurs == null || maxOccurs.isEmpty()) return false;
        try {
            return Integer.parseInt(maxOccurs) > 1;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
