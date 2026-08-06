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
 *   <li>xsd:group references (the referenced group's content model is expanded in place)</li>
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

    private static final String UNBOUNDED = "unbounded";
    /** {@code minOccurs="0"} — the only value that makes an element optional. */
    private static final String MIN_OCCURS_OPTIONAL = "0";

    /**
     * The schema document a reference is being resolved against, plus the complexType and group
     * declarations currently being expanded — the recursion guard that keeps self-referential
     * declarations from looping.
     */
    private record XsdContext(Element schemaRoot, Set<Element> visiting) {
        /** The same traversal, continued in another schema document (an import or include). */
        XsdContext withRoot(Element root) {
            return root == schemaRoot ? this : new XsdContext(root, visiting);
        }
    }

    private final Map<String, List<Element>> schemasByNamespace;

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
        return convertParts(messages.getFirst().getParts(), new HashSet<>());
    }

    /**
     * Converts a list of WSDL message parts to an OpenAPI schema. A single part with a wrapping
     * XSD element (document/literal wrapped style) is unwrapped to that element's own schema.
     * Any other case — RPC-style parts (type=), or multiple parts (bare style) — is represented
     * as an object with one property per part, keyed by the part's name.
     */
    public Schema<?> convertParts(List<Part> parts) {
        return convertParts(parts, new HashSet<>());
    }

    private Schema<?> convertParts(List<Part> parts, Set<Element> visiting) {
        if (parts.isEmpty()) return new ObjectSchema();
        if (parts.size() == 1 && parts.getFirst().getElementQName() != null) {
            return convert(parts.getFirst().getElementQName(), visiting);
        }
        var schema = new ObjectSchema();
        for (var part : parts) {
            QName elementQName = part.getElementQName();
            schema.addProperty(part.getName(),
                    elementQName != null ? convert(elementQName, visiting) : convertType(part.getTypeQName(), visiting));
        }
        return schema;
    }

    /**
     * Converts the top-level XSD element referenced by the given QName to an OpenAPI schema.
     */
    public Schema<?> convert(QName qname) {
        return convert(qname, new HashSet<>());
    }

    private Schema<?> convert(QName qname, Set<Element> visiting) {
        List<Element> roots = schemasByNamespace.get(qname.getNamespaceURI());
        if (roots == null) return new ObjectSchema();
        for (var schemaRoot : roots) {
            Element xsdElement = findXsdChildWithName(schemaRoot, "element", qname.getLocalPart());
            if (xsdElement != null) {
                return convertElementType(xsdElement, new XsdContext(schemaRoot, visiting));
            }
        }
        return new ObjectSchema();
    }

    /**
     * Resolves the Schema for an {@code <xsd:element>} node — its inline type
     * or the type referenced by the {@code type} attribute.
     * Does NOT apply maxOccurs wrapping; that is done by addElementField for sequence members.
     */
    private Schema<?> convertElementType(Element xsdElement, XsdContext ctx) {
        Element inlineComplexType = findXsdChild(xsdElement, "complexType");
        if (inlineComplexType != null) {
            return buildObjectSchema(inlineComplexType, ctx);
        }
        Element inlineSimpleType = findXsdChild(xsdElement, "simpleType");
        if (inlineSimpleType != null) {
            return buildSimpleTypeSchema(inlineSimpleType, ctx);
        }
        String typeAttr = xsdElement.getAttribute("type");
        if (!typeAttr.isEmpty()) {
            return resolveTypeRef(typeAttr, xsdElement, ctx);
        }
        return new ObjectSchema();
    }

    private Schema<?> buildObjectSchema(Element complexTypeEl, XsdContext ctx) {
        var objectSchema = new ObjectSchema();

        Element particle = firstXsdChild(complexTypeEl, "sequence", "all", "choice", "group");
        if (particle != null) {
            addParticleFields(particle, objectSchema, ctx);
            addAttributeFields(complexTypeEl, objectSchema, ctx);
            return objectSchema;
        }
        Element complexContent = findXsdChild(complexTypeEl, "complexContent");
        if (complexContent != null) {
            Element extension = findXsdChild(complexContent, "extension");
            if (extension != null) addExtensionFields(extension, objectSchema, ctx);
            Element restriction = findXsdChild(complexContent, "restriction");
            if (restriction != null) addExtensionFields(restriction, objectSchema, ctx);
            return objectSchema;
        }
        // simpleContent: a complex type whose value is text — approximate as string
        if (findXsdChild(complexTypeEl, "simpleContent") != null) {
            return new StringSchema();
        }
        addAttributeFields(complexTypeEl, objectSchema, ctx);
        return objectSchema;
    }

    /**
     * Expands one {@code <xsd:sequence>}, {@code <xsd:all>}, {@code <xsd:choice>} or
     * {@code <xsd:group ref=.../>} particle into {@code schema}.
     */
    private void addParticleFields(Element particle, ObjectSchema schema, XsdContext ctx) {
        switch (particle.getLocalName()) {
            case "choice" -> addChoiceFields(particle, schema, ctx);
            case "group"  -> addGroupFields(particle, schema, ctx);
            default       -> addContainerFields(particle, schema, ctx);
        }
    }

    /**
     * Processes children of {@code <xsd:sequence>} or {@code <xsd:all>}, dispatching
     * element, choice, nested sequence/all, and group-ref nodes.
     */
    private void addContainerFields(Element container, ObjectSchema schema, XsdContext ctx) {
        for (Element el : xsdChildren(container)) {
            switch (el.getLocalName()) {
                case "element"          -> addElementField(el, schema, ctx);
                case "choice"           -> addChoiceFields(el, schema, ctx);
                case "sequence", "all"  -> addContainerFields(el, schema, ctx);
                case "group"            -> addGroupFields(el, schema, ctx);
                // xsd:any: skip - wildcard has no JSON Schema equivalent
            }
        }
    }

    /**
     * Resolves an {@code <xsd:group ref="prefix:name"/>} by looking up the named group
     * definition in the schema map and expanding its content model (sequence, all, or choice)
     * into {@code schema}. A group that transitively references itself is expanded once and
     * then skipped. Logs a debug message if the group cannot be resolved.
     */
    private void addGroupFields(Element groupRef, ObjectSchema schema, XsdContext ctx) {
        String ref = groupRef.getAttribute("ref");
        if (ref.isEmpty()) return;
        String local = localName(ref);
        for (var root : resolveTargetSchemaRoots(prefix(ref), groupRef, ctx.schemaRoot(), schemasByNamespace)) {
            Element groupDef = findXsdChildWithName(root, "group", local);
            if (groupDef != null) {
                expandGroupDefinition(groupDef, local, schema, ctx.withRoot(root));
                return;
            }
        }
        log.debug("xsd:group ref='{}' could not be resolved, skipping", ref);
    }

    /** Expands a resolved {@code <xsd:group name=.../>} definition, guarding against reference cycles. */
    private void expandGroupDefinition(Element groupDef, String name, ObjectSchema schema, XsdContext ctx) {
        if (ctx.visiting().contains(groupDef)) {
            log.debug("Recursive reference to group '{}', skipping", name);
            return;
        }
        ctx.visiting().add(groupDef);
        try {
            Element particle = firstXsdChild(groupDef, "sequence", "all", "choice");
            if (particle != null) addParticleFields(particle, schema, ctx);
        } finally {
            ctx.visiting().remove(groupDef);
        }
    }

    private void addElementField(Element el, ObjectSchema schema, XsdContext ctx) {
        String fieldName = el.getAttribute("name");
        if (fieldName.isEmpty()) {
            addRefField(el, schema, ctx);
            return;
        }
        addField(schema, fieldName, el, convertElementType(el, ctx));
    }

    /**
     * Resolves an {@code <xsd:element ref="prefix:local"/>} by looking up the referenced global
     * element and adding it as a property under its declared name.
     */
    private void addRefField(Element refEl, ObjectSchema schema, XsdContext ctx) {
        resolveRefAlternative(refEl, ctx)
                .ifPresent(alternative -> addField(schema, alternative.localName(), refEl, alternative.fieldSchema()));
    }

    /** Adds {@code fieldSchema} as a property, applying the declaration's maxOccurs and minOccurs. */
    private static void addField(ObjectSchema schema, String fieldName, Element declaration, Schema<?> fieldSchema) {
        schema.addProperty(fieldName, applyMaxOccurs(declaration, fieldSchema));
        if (!MIN_OCCURS_OPTIONAL.equals(declaration.getAttribute("minOccurs"))) {
            schema.addRequiredItem(fieldName);
        }
    }

    /** Wraps {@code fieldSchema} in an ArraySchema if the declaration allows more than one occurrence. */
    private static Schema<?> applyMaxOccurs(Element declaration, Schema<?> fieldSchema) {
        String maxOccurs = declaration.getAttribute("maxOccurs");
        if (UNBOUNDED.equals(maxOccurs) || isMoreThanOne(maxOccurs)) {
            return new ArraySchema().items(fieldSchema);
        }
        return fieldSchema;
    }

    /**
     * Maps direct {@code <xsd:attribute>} children of {@code container} (a complexType or
     * extension/restriction element) to properties prefixed with {@code @}, e.g. an attribute
     * named {@code id} becomes the property {@code @id}.
     */
    private void addAttributeFields(Element container, ObjectSchema schema, XsdContext ctx) {
        for (Element el : xsdChildren(container)) {
            if (!"attribute".equals(el.getLocalName())) continue;

            String fieldName = el.getAttribute("name");
            if (fieldName.isEmpty()) continue; // ref= attributes: not supported

            String key = attributeKey(fieldName);
            schema.addProperty(key, convertElementType(el, ctx));
            if ("required".equals(el.getAttribute("use"))) {
                schema.addRequiredItem(key);
            }
        }
    }

    /** A resolved xsd:choice alternative, prior to deciding its final (possibly-qualified) key. */
    private record ChoiceAlternative(String localName, String namespaceURI, Schema<?> fieldSchema) {
        ChoiceAlternative withSchema(Schema<?> schema) {
            return new ChoiceAlternative(localName, namespaceURI, schema);
        }
    }

    /**
     * Maps choice alternatives to optional properties. All alternatives are present
     * in the schema but none are added to {@code required}, reflecting that exactly
     * one is expected at runtime.
     *
     * <p>Direct {@code xsd:element} alternatives are collected first so that same-local-name
     * collisions across namespaces can be detected and keyed with a namespace-qualified key
     * ({@link XsdDomUtil#qualifiedKey}) instead of silently overwriting each other.
     * Nested {@code xsd:sequence} and {@code xsd:all} particles are expanded inline via
     * {@link #addContainerFields}; nested {@code xsd:choice} particles recurse into this method.
     */
    private void addChoiceFields(Element choice, ObjectSchema schema, XsdContext ctx) {
        var alternatives = new ArrayList<ChoiceAlternative>();
        for (Element el : xsdChildren(choice)) {
            switch (el.getLocalName()) {
                case "element" -> {
                    String fieldName = el.getAttribute("name");
                    if (!fieldName.isEmpty()) {
                        String ns = ctx.schemaRoot().getAttribute("targetNamespace");
                        alternatives.add(new ChoiceAlternative(fieldName, ns.isEmpty() ? null : ns, convertElementType(el, ctx)));
                    } else {
                        resolveRefAlternative(el, ctx)
                                .map(alternative -> alternative.withSchema(applyMaxOccurs(el, alternative.fieldSchema())))
                                .ifPresent(alternatives::add);
                    }
                }
                case "sequence", "all" -> addContainerFields(el, schema, ctx);
                case "choice"          -> addChoiceFields(el, schema, ctx);
            }
        }

        var colliding = collidingLocalNames(alternatives);
        for (var alt : alternatives) {
            boolean collides = colliding.contains(alt.localName()) && alt.namespaceURI() != null;
            schema.addProperty(collides ? qualifiedKey(alt.namespaceURI(), alt.localName()) : alt.localName(), alt.fieldSchema());
            // intentionally not added to required
        }
    }

    /** The local names carried by more than one alternative — those need a namespace-qualified key. */
    private static Set<String> collidingLocalNames(List<ChoiceAlternative> alternatives) {
        var seen = new HashSet<String>();
        var colliding = new HashSet<String>();
        for (var alt : alternatives) {
            if (!seen.add(alt.localName())) colliding.add(alt.localName());
        }
        return colliding;
    }

    /**
     * Resolves an {@code <xsd:element ref="prefix:local"/>} to the referenced element's local name,
     * namespace, and schema, without mutating a target schema and without applying the reference's
     * own maxOccurs — so that {@link #addChoiceFields} can detect same-local-name collisions across
     * namespaces first, and {@link #addRefField} can apply minOccurs/maxOccurs itself.
     */
    private Optional<ChoiceAlternative> resolveRefAlternative(Element refEl, XsdContext ctx) {
        String ref = refEl.getAttribute("ref");
        if (ref.isEmpty()) return Optional.empty();
        String local = localName(ref);
        String refPrefix = prefix(ref);
        String namespaceURI = refPrefix.isEmpty()
                ? ctx.schemaRoot().getAttribute("targetNamespace")
                : refEl.lookupNamespaceURI(refPrefix);
        for (var root : resolveTargetSchemaRoots(refPrefix, refEl, ctx.schemaRoot(), schemasByNamespace)) {
            Element referenced = findXsdChildWithName(root, "element", local);
            if (referenced != null) {
                return Optional.of(new ChoiceAlternative(local, namespaceURI, convertElementType(referenced, ctx.withRoot(root))));
            }
        }
        return Optional.empty();
    }

    /**
     * Handles {@code <xsd:extension>} and (for field-extraction purposes)
     * {@code <xsd:restriction>} inside {@code <xsd:complexContent>}.
     * Merges base type fields into {@code schema} first, then appends extension fields.
     */
    private void addExtensionFields(Element extension, ObjectSchema schema, XsdContext ctx) {
        String base = extension.getAttribute("base");
        if (!base.isEmpty()) {
            Schema<?> baseSchema = resolveTypeRef(base, extension, ctx);
            if (baseSchema instanceof ObjectSchema baseObj && baseObj.getProperties() != null) {
                baseObj.getProperties().forEach(schema::addProperty);
                if (baseObj.getRequired() != null) {
                    baseObj.getRequired().forEach(schema::addRequiredItem);
                }
            } else if (!(baseSchema instanceof ObjectSchema)) {
                log.debug("Base type '{}' is not an object schema, skipping field inheritance", base);
            }
        }
        Element particle = firstXsdChild(extension, "sequence", "all", "choice", "group");
        if (particle != null) addParticleFields(particle, schema, ctx);
        addAttributeFields(extension, schema, ctx);
    }

    private Schema<?> buildSimpleTypeSchema(Element simpleTypeEl, XsdContext ctx) {
        Element restriction = findXsdChild(simpleTypeEl, "restriction");
        if (restriction == null) return new StringSchema();
        String base = restriction.getAttribute("base");
        Schema<?> schema = base.isEmpty() ? new StringSchema() : resolveTypeRef(base, restriction, ctx);
        addEnumValues(restriction, schema);
        return schema;
    }

    @SuppressWarnings("unchecked")
    private void addEnumValues(Element restriction, Schema<?> schema) {
        for (Element el : xsdChildren(restriction)) {
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
    private Schema<?> resolveTypeRef(String typeRef, Element contextElement, XsdContext ctx) {
        if (typeRef.isEmpty()) return new StringSchema();
        String prefix = prefix(typeRef);
        String local = localName(typeRef);
        List<Element> targetRoots = resolveTargetSchemaRoots(prefix, contextElement, ctx.schemaRoot(), schemasByNamespace);
        for (var targetRoot : targetRoots) {
            Element complexType = findXsdChildWithName(targetRoot, "complexType", local);
            if (complexType != null) {
                if (ctx.visiting().contains(complexType)) {
                    log.debug("Recursive reference to type '{}', returning empty schema", local);
                    return new ObjectSchema();
                }
                ctx.visiting().add(complexType);
                try {
                    return buildObjectSchema(complexType, ctx.withRoot(targetRoot));
                } finally {
                    ctx.visiting().remove(complexType);
                }
            }
        }
        for (var targetRoot : targetRoots) {
            Element simpleType = findXsdChildWithName(targetRoot, "simpleType", local);
            if (simpleType != null) {
                return buildSimpleTypeSchema(simpleType, ctx.withRoot(targetRoot));
            }
        }
        return mapPrimitive(local);
    }

    /**
     * Resolves a WSDL message part's {@code type=} reference (already resolved to a QName, as
     * used by RPC-style bindings) to an OpenAPI schema.
     */
    public Schema<?> convertType(QName qname) {
        return convertType(qname, new HashSet<>());
    }

    private Schema<?> convertType(QName qname, Set<Element> visiting) {
        if (qname == null) return new StringSchema();
        if (XSD_NS.equals(qname.getNamespaceURI())) return mapPrimitive(qname.getLocalPart());
        List<Element> targetRoots = schemasByNamespace.get(qname.getNamespaceURI());
        if (targetRoots == null) return mapPrimitive(qname.getLocalPart());
        for (var root : targetRoots) {
            Element complexType = findXsdChildWithName(root, "complexType", qname.getLocalPart());
            if (complexType != null) return buildObjectSchema(complexType, new XsdContext(root, visiting));
        }
        for (var root : targetRoots) {
            Element simpleType = findXsdChildWithName(root, "simpleType", qname.getLocalPart());
            if (simpleType != null) return buildSimpleTypeSchema(simpleType, new XsdContext(root, visiting));
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


    private static boolean isMoreThanOne(String maxOccurs) {
        if (maxOccurs == null || maxOccurs.isEmpty()) return false;
        try {
            return Integer.parseInt(maxOccurs) > 1;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
