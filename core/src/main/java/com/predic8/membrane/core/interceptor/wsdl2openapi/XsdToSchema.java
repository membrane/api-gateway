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
import com.predic8.membrane.core.util.wsdl.parser.Part;
import io.swagger.v3.oas.models.media.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.namespace.QName;
import java.util.*;

import static com.predic8.membrane.annot.Constants.XSD_NS;
import static com.predic8.membrane.core.interceptor.wsdl2openapi.XsdDomUtil.*;

/**
 * Converts XSD type definitions embedded in a WSDL to OpenAPI Schema objects.
 *
 * <p>Handles:
 * <ul>
 *   <li>Inline complexType and type-reference patterns</li>
 *   <li>xsd:sequence, xsd:all (treated identically)</li>
 *   <li>xsd:choice (all alternatives become optional properties)</li>
 *   <li>xsd:attribute (mapped to a property named "@" + attribute name; required when use="required")</li>
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

    private final Map<String, List<Element>> schemasByNamespace;
    private final Set<Element> inProgress = new HashSet<>();

    XsdToSchema(Map<String, List<Element>> schemasByNamespace) {
        this.schemasByNamespace = schemasByNamespace;
    }

    public XsdToSchema(Definitions definitions) {
        this(buildSchemaMap(definitions));
    }

    /**
     * Converts the parts of the first message in the list to an OpenAPI schema.
     * Returns an empty ObjectSchema if the list is empty or has no usable parts.
     */
    public Schema<?> convertMessageParts(List<Message> messages) {
        if (messages.isEmpty()) return new ObjectSchema();
        return convertParts(messages.getFirst().getParts());
    }

    /**
     * Converts a list of WSDL message parts to an OpenAPI schema. A single part with a wrapping
     * XSD element (document/literal wrapped style) is unwrapped to that element's own schema.
     * Any other case — RPC-style parts (type=), or multiple parts (bare style) — is represented
     * as an object with one property per part, keyed by the part's name.
     */
    public Schema<?> convertParts(List<Part> parts) {
        if (parts.isEmpty()) return new ObjectSchema();
        if (parts.size() == 1 && parts.getFirst().getElementQName() != null) {
            return convert(parts.getFirst().getElementQName());
        }
        var schema = new ObjectSchema();
        for (var part : parts) {
            QName elementQName = part.getElementQName();
            schema.addProperty(part.getName(),
                    elementQName != null ? convert(elementQName) : convertType(part.getTypeQName()));
        }
        return schema;
    }

    /**
     * Converts the top-level XSD element referenced by the given QName to an OpenAPI schema.
     */
    public Schema<?> convert(QName qname) {
        List<Element> roots = schemasByNamespace.get(qname.getNamespaceURI());
        if (roots == null) return new ObjectSchema();
        for (var schemaRoot : roots) {
            Element xsdElement = findXsdChildWithName(schemaRoot, "element", qname.getLocalPart());
            if (xsdElement != null) {
                return convertElementType(xsdElement, schemaRoot);
            }
        }
        return new ObjectSchema();
    }

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
            addAttributeFields(complexTypeEl, objectSchema, schemaRoot);
            return objectSchema;
        }
        Element all = findXsdChild(complexTypeEl, "all");
        if (all != null) {
            addContainerFields(all, objectSchema, schemaRoot);
            addAttributeFields(complexTypeEl, objectSchema, schemaRoot);
            return objectSchema;
        }
        Element choice = findXsdChild(complexTypeEl, "choice");
        if (choice != null) {
            addChoiceFields(choice, objectSchema, schemaRoot);
            addAttributeFields(complexTypeEl, objectSchema, schemaRoot);
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
        addAttributeFields(complexTypeEl, objectSchema, schemaRoot);
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
        if (fieldName.isEmpty()) {
            addRefField(el, schema, schemaRoot);
            return;
        }

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
     * Resolves an {@code <xsd:element ref="prefix:local"/>} by looking up the referenced global
     * element and adding it as a property under its declared name.
     */
    private void addRefField(Element refEl, ObjectSchema schema, Element schemaRoot) {
        String ref = refEl.getAttribute("ref");
        if (ref.isEmpty()) return;
        String local = localName(ref);
        for (var root : resolveTargetSchemaRoots(prefix(ref), refEl, schemaRoot, schemasByNamespace)) {
            Element referenced = findXsdChildWithName(root, "element", local);
            if (referenced != null) {
                String minOccurs = refEl.getAttribute("minOccurs");
                String maxOccurs = refEl.getAttribute("maxOccurs");
                Schema<?> fieldSchema = convertElementType(referenced, root);
                if ("unbounded".equals(maxOccurs) || isMoreThanOne(maxOccurs)) {
                    fieldSchema = new ArraySchema().items(fieldSchema);
                }
                schema.addProperty(local, fieldSchema);
                if (!"0".equals(minOccurs)) {
                    schema.addRequiredItem(local);
                }
                return;
            }
        }
    }

    /**
     * Maps direct {@code <xsd:attribute>} children of {@code container} (a complexType or
     * extension/restriction element) to properties prefixed with {@code @}, e.g. an attribute
     * named {@code id} becomes the property {@code @id}.
     */
    private void addAttributeFields(Element container, ObjectSchema schema, Element schemaRoot) {
        NodeList children = container.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (!(child instanceof Element el) || !XSD_NS.equals(el.getNamespaceURI())) continue;
            if (!"attribute".equals(el.getLocalName())) continue;

            String fieldName = el.getAttribute("name");
            if (fieldName.isEmpty()) continue; // ref= attributes: not supported

            schema.addProperty("@" + fieldName, convertElementType(el, schemaRoot));
            if ("required".equals(el.getAttribute("use"))) {
                schema.addRequiredItem("@" + fieldName);
            }
        }
    }

    /** A resolved xsd:choice alternative, prior to deciding its final (possibly-qualified) key. */
    private record ChoiceAlternative(String localName, String namespaceURI, Schema<?> fieldSchema) {}

    /**
     * Maps choice alternatives to optional properties. All alternatives are present
     * in the schema but none are added to {@code required}, reflecting that exactly
     * one is expected at runtime.
     *
     * <p>Alternatives are resolved before any are added to {@code schema}, so that alternatives
     * whose local name collides across namespaces (e.g. two {@code ref}'d elements both named
     * {@code value}, from different namespaces) can be detected and keyed with a namespace-
     * qualified key ({@link XsdDomUtil#qualifiedKey}) instead of silently overwriting each other.
     */
    private void addChoiceFields(Element choice, ObjectSchema schema, Element schemaRoot) {
        var alternatives = new ArrayList<ChoiceAlternative>();
        NodeList children = choice.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (!(child instanceof Element el) || !XSD_NS.equals(el.getNamespaceURI())) continue;
            if (!"element".equals(el.getLocalName())) continue;

            String fieldName = el.getAttribute("name");
            if (!fieldName.isEmpty()) {
                alternatives.add(new ChoiceAlternative(fieldName, null, convertElementType(el, schemaRoot)));
            } else {
                resolveChoiceRefAlternative(el, schemaRoot).ifPresent(alternatives::add);
            }
        }

        var occurrences = new HashMap<String, Integer>();
        for (var alt : alternatives) {
            occurrences.merge(alt.localName(), 1, Integer::sum);
        }
        for (var alt : alternatives) {
            boolean collides = occurrences.get(alt.localName()) > 1 && alt.namespaceURI() != null;
            String key = collides ? qualifiedKey(alt.namespaceURI(), alt.localName()) : alt.localName();
            schema.addProperty(key, alt.fieldSchema());
            // intentionally not added to required
        }
    }

    /**
     * Resolves an {@code <xsd:element ref="prefix:local"/>} choice alternative to its local name,
     * referenced namespace, and schema, without mutating a target schema directly — so that
     * {@link #addChoiceFields} can detect same-local-name collisions across namespaces first.
     */
    private Optional<ChoiceAlternative> resolveChoiceRefAlternative(Element refEl, Element schemaRoot) {
        String ref = refEl.getAttribute("ref");
        if (ref.isEmpty()) return Optional.empty();
        String local = localName(ref);
        String refPrefix = prefix(ref);
        String namespaceURI = refPrefix.isEmpty()
                ? schemaRoot.getAttribute("targetNamespace")
                : refEl.lookupNamespaceURI(refPrefix);
        for (var root : resolveTargetSchemaRoots(refPrefix, refEl, schemaRoot, schemasByNamespace)) {
            Element referenced = findXsdChildWithName(root, "element", local);
            if (referenced != null) {
                String maxOccurs = refEl.getAttribute("maxOccurs");
                Schema<?> fieldSchema = convertElementType(referenced, root);
                if ("unbounded".equals(maxOccurs) || isMoreThanOne(maxOccurs)) {
                    fieldSchema = new ArraySchema().items(fieldSchema);
                }
                return Optional.of(new ChoiceAlternative(local, namespaceURI, fieldSchema));
            }
        }
        return Optional.empty();
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
            } else if (!(baseSchema instanceof ObjectSchema)) {
                log.debug("Base type '{}' is not an object schema, skipping field inheritance", base);
            }
        }
        Element seq = findXsdChild(extension, "sequence");
        if (seq != null) addContainerFields(seq, schema, schemaRoot);
        Element all = findXsdChild(extension, "all");
        if (all != null) addContainerFields(all, schema, schemaRoot);
        addAttributeFields(extension, schema, schemaRoot);
    }

    private Schema<?> buildSimpleTypeSchema(Element simpleTypeEl, Element contextEl, Element schemaRoot) {
        Element restriction = findXsdChild(simpleTypeEl, "restriction");
        if (restriction == null) return new StringSchema();
        String base = restriction.getAttribute("base");
        Schema<?> schema = base.isEmpty() ? new StringSchema() : resolveTypeRef(base, restriction, schemaRoot);
        addEnumValues(restriction, schema);
        return schema;
    }

    @SuppressWarnings("unchecked")
    private void addEnumValues(Element restriction, Schema<?> schema) {
        NodeList children = restriction.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (!(child instanceof Element el) || !XSD_NS.equals(el.getNamespaceURI())) continue;
            switch (el.getLocalName()) {
                case "enumeration" -> ((Schema<Object>) schema).addEnumItemObject(el.getAttribute("value"));
                case "pattern"     -> schema.setPattern(el.getAttribute("value"));
            }
        }
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
        List<Element> targetRoots = resolveTargetSchemaRoots(prefix, contextElement, currentSchemaRoot, schemasByNamespace);
        for (var targetRoot : targetRoots) {
            Element complexType = findXsdChildWithName(targetRoot, "complexType", local);
            if (complexType != null) {
                if (inProgress.contains(complexType)) {
                    log.debug("Recursive reference to type '{}', returning empty schema", local);
                    return new ObjectSchema();
                }
                inProgress.add(complexType);
                try {
                    return buildObjectSchema(complexType, targetRoot);
                } finally {
                    inProgress.remove(complexType);
                }
            }
        }
        for (var targetRoot : targetRoots) {
            Element simpleType = findXsdChildWithName(targetRoot, "simpleType", local);
            if (simpleType != null) {
                return buildSimpleTypeSchema(simpleType, contextElement, targetRoot);
            }
        }
        return mapPrimitive(local);
    }

    /**
     * Resolves a WSDL message part's {@code type=} reference (already resolved to a QName, as
     * used by RPC-style bindings) to an OpenAPI schema.
     */
    public Schema<?> convertType(QName qname) {
        if (qname == null) return new StringSchema();
        if (XSD_NS.equals(qname.getNamespaceURI())) return mapPrimitive(qname.getLocalPart());
        List<Element> targetRoots = schemasByNamespace.get(qname.getNamespaceURI());
        if (targetRoots == null) return mapPrimitive(qname.getLocalPart());
        for (var root : targetRoots) {
            Element complexType = findXsdChildWithName(root, "complexType", qname.getLocalPart());
            if (complexType != null) return buildObjectSchema(complexType, root);
        }
        for (var root : targetRoots) {
            Element simpleType = findXsdChildWithName(root, "simpleType", qname.getLocalPart());
            if (simpleType != null) return buildSimpleTypeSchema(simpleType, simpleType, root);
        }
        return mapPrimitive(qname.getLocalPart());
    }

    private Schema<?> mapPrimitive(String localPart) {
        return switch (localPart) {
            case "string" -> new StringSchema();
            case "date" -> withFormat(new StringSchema(), "date");
            case "dateTime" -> withFormat(new StringSchema(), "date-time");
            case "base64Binary" -> withFormat(new StringSchema(), "byte");
            case "hexBinary" -> withFormat(new StringSchema(), "binary");
            case "anyURI", "normalizedString", "token", "language", "time",
                 "gYear", "gMonth", "gDay", "gYearMonth", "gMonthDay", "duration",
                 "QName", "NOTATION" -> withDescription(new StringSchema(), localPart);
            case "int" -> withFormat(new IntegerSchema(), "int32");
            case "long" -> withFormat(new IntegerSchema(), "int64");
            case "integer", "short", "byte",
                 "nonNegativeInteger", "positiveInteger",
                 "nonPositiveInteger", "negativeInteger",
                 "unsignedInt", "unsignedShort", "unsignedByte", "unsignedLong" -> withDescription(withFormat(new IntegerSchema(), null), localPart);
            case "float" -> withFormat(new NumberSchema(), "float");
            case "double" -> withFormat(new NumberSchema(), "double");
            case "decimal" -> withDescription(new NumberSchema(), localPart);
            case "boolean" -> new BooleanSchema();
            default -> {
                log.debug("Unknown XSD type '{}', defaulting to string", localPart);
                yield new StringSchema();
            }
        };
    }

    private static <T extends Schema<?>> T withFormat(T schema, String format) {
        schema.setFormat(format);
        return schema;
    }

    private static <T extends Schema<?>> T withDescription(T schema, String xsdType) {
        schema.setDescription("xsd:" + xsdType);
        return schema;
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
