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
import com.predic8.membrane.core.util.wsdl.parser.Operation;
import com.predic8.membrane.core.util.wsdl.parser.Part;
import io.swagger.v3.oas.models.media.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import javax.xml.namespace.QName;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.predic8.membrane.annot.Constants.XSD_NS;
import static com.predic8.membrane.core.interceptor.wsdl2openapi.XsdContentModel.*;
import static com.predic8.membrane.core.interceptor.wsdl2openapi.XsdDomUtil.*;
import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;

/**
 * Converts XSD type definitions embedded in a WSDL to OpenAPI Schema objects.
 *
 * <p>Handles:
 * <ul>
 *   <li>Inline complexType and type-reference patterns</li>
 *   <li>xsd:sequence, xsd:all (treated identically)</li>
 *   <li>xsd:choice (all alternatives become optional properties, plus a sibling {@code oneOf} that
 *       requires exactly one of them — see {@link #addChoiceFields})</li>
 *   <li>xsd:group references (the referenced group's content model is expanded in place)</li>
 *   <li>xsd:attribute (mapped to a property named "@" + attribute name; required when use="required")</li>
 *   <li>xsd:complexContent/xsd:extension (base type fields are inherited)</li>
 *   <li>xsd:complexContent/xsd:restriction (its own content model only — a restriction inherits
 *       nothing, so a base field it omits is absent)</li>
 *   <li>xsd:simpleContent (the base type's value; with attributes, an object holding the value
 *       under "$value" alongside the "@"-prefixed attributes)</li>
 *   <li>Named and inline xsd:simpleType restrictions (resolved to the base primitive)</li>
 *   <li>xsd:restriction facets: enumeration, pattern, length, minLength, maxLength,
 *       minInclusive, maxInclusive, minExclusive, maxExclusive</li>
 *   <li>nillable="true" (produces a nullable schema)</li>
 *   <li>maxOccurs="unbounded" or > 1 (produces ArraySchema)</li>
 *   <li>Cross-namespace type references (resolved via the full import graph)</li>
 * </ul>
 *
 * <p>The features above are constraints: they describe which messages are valid, and are enforced
 * wherever the generated schema is validated against.
 *
 * <p>A named {@code xsd:complexType} or {@code xsd:simpleType} is converted once and published as a
 * {@code components/schemas} entry (see {@link #getComponents()}), which every use site refers to by
 * {@code $ref}. That keeps the type's name — the one the XSD gave it — in the document and in the
 * clients generated from it, and lets a self-referential type be expressed at all. Types declared
 * inline have no name to publish and stay where they are.
 *
 * <p>Two use sites resolve a named type in place instead: a declaration that writes onto the schema
 * (see {@link #mutates}) would otherwise change the type for everyone using it, and the base type of
 * a derivation is flattened into the derived type rather than referenced.
 *
 * <p>{@code default=} on elements and attributes is carried over as the OpenAPI {@code default}
 * keyword, which is an annotation rather than a constraint: it documents the value for clients,
 * Swagger UI and code generators, and is deliberately not enforced. {@code fixed=} produces that
 * same {@code default} plus a single-value {@code enum}, because a fixed declaration does restrict
 * which values are valid.
 *
 * <p>An XSD primitive becomes the JSON type it corresponds to, plus a {@code format} where one is
 * conventional ({@code date}, {@code date-time}, {@code uri}, {@code time}, {@code duration},
 * {@code decimal}, …). Where that pair does not name the XSD type it came from — {@code xsd:token},
 * the {@code g*} date types, the bounded and unbounded integer types, {@code xsd:decimal} — the
 * type name is carried in an {@code x-xsd-type} extension, so that a tool reading the document can
 * recover it. {@code description} is left to the service's own documentation.
 *
 * <p>{@code xsd:decimal} is emitted as a JSON {@code number} with {@code format: decimal}. JSON
 * {@code number} is arbitrary-precision by specification, but most parsers realize it as a double,
 * so a value beyond double precision may be rounded by tools this document is fed to. That is
 * accepted here rather than mapped to {@code type: string}, which would keep the precision but
 * cost every consumer the numeric type and any range facets stated on it.
 *
 * <p>Neither is applied to messages during SOAP/JSON conversion. Supplying a missing value is the
 * SOAP service's own responsibility — and in XSD an element's default only applies to an element
 * that is present but empty, never to an absent one, so filling in omitted fields here would not
 * even match what the service does.
 */
public class XsdToSchema {

    private static final Logger log = LoggerFactory.getLogger(XsdToSchema.class);

    private static final String UNBOUNDED = "unbounded";
    /** Carries the originating XSD type where {@code type}/{@code format} do not — see {@link #withXsdType}. */
    static final String XSD_TYPE_EXTENSION = "x-xsd-type";
    /** The OpenAPI 3.1 way of saying a value may be absent — see {@link #makeNullable}. */
    private static final String NULL_TYPE = "null";
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

    private enum Kind { COMPLEX, SIMPLE }

    /** A named type declaration, together with the schema document that declares it. */
    private record NamedType(Element element, Element schemaRoot, Kind kind) {}

    private final Map<String, List<Element>> schemasByNamespace;

    /** Resolves a particle's content model — group references expanded, cycles cut. */
    private final XsdContentModel contentModel;

    /** The component name of every named type the schemas declare, keyed by {@link XsdDomUtil#qualifiedKey}. */
    private final Map<String, String> componentNames;

    /**
     * The named types reached so far, keyed by component name. Filled as conversion walks the
     * schemas, so a type no message refers to is not published.
     */
    private final Map<String, Schema<?>> components = new LinkedHashMap<>();

    XsdToSchema(Map<String, List<Element>> schemasByNamespace, Set<String> reservedComponentNames) {
        this.schemasByNamespace = schemasByNamespace;
        this.componentNames = buildComponentNames(schemasByNamespace, reservedComponentNames);
        this.contentModel = new XsdContentModel(schemasByNamespace);
    }

    public XsdToSchema(Definitions definitions, Set<String> reservedComponentNames) {
        this(buildSchemaMap(definitions), reservedComponentNames);
    }

    /**
     * The schemas of the named types reached during conversion, to be published as
     * {@code components/schemas} of the document the references produced here point into.
     */
    Map<String, Schema<?>> getComponents() {
        return components;
    }

    /**
     * The schema documents of the WSDL's whole import graph, by target namespace — resolved once
     * here so that the runtime transformers do not each walk the graph again.
     */
    Map<String, List<Element>> getSchemasByNamespace() {
        return schemasByNamespace;
    }

    /**
     * The schema of a named type, as a reference to the component it is published as. The component
     * is built on first use; a type reached again — including one that refers back to itself — yields
     * the same reference without being built twice, which is what makes a recursive type expressible.
     *
     * <p>Falls back to building the type inline when it has no component name, which only a schema
     * outside the traversed import graph can be.
     */
    private Schema<?> componentRef(String namespace, String localName, Supplier<Schema<?>> build) {
        String name = componentNames.get(qualifiedKey(namespace, localName));
        if (name == null) {
            log.debug("No component name for type '{}' in namespace '{}', inlining it", localName, namespace);
            return build.get();
        }
        if (components.containsKey(name)) return refTo(name);
        // Registered before it is built, so a reference the type makes to itself finds it and stops.
        components.put(name, new ObjectSchema());
        components.put(name, build.get());
        return refTo(name);
    }

    private static Schema<?> refTo(String componentName) {
        return new Schema<>().$ref(COMPONENTS_SCHEMAS_PREFIX + componentName);
    }

    /** The component {@code schema} references, or {@code schema} itself if it references none. */
    private Schema<?> dereference(Schema<?> schema) {
        return XsdDomUtil.dereference(components, schema);
    }

    /**
     * Whether a declaration modifies the type it names, which rules out referencing a shared
     * component: nillability and a default or fixed value are written onto the schema itself, and
     * they belong to this one declaration rather than to every user of the type.
     */
    private static boolean mutates(Element declaration) {
        return "true".equals(declaration.getAttribute("nillable"))
                || !declaration.getAttribute("default").isEmpty()
                || !declaration.getAttribute("fixed").isEmpty();
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
     * The schema of a SOAP {@code <detail>} element carrying one of the operation's declared faults:
     * an object with one optional property per fault, keyed by the name the fault element appears
     * under in the detail. Only one property is ever present in a given response, which is why the
     * published OpenAPI document turns these properties into a {@code oneOf}.
     * <p>
     * Returns an empty ObjectSchema when the operation declares no faults.
     */
    public Schema<?> convertFaultDetail(List<Operation.Fault> faults) {
        var visiting = new HashSet<Element>();
        var schema = new ObjectSchema();
        for (Operation.Fault fault : faults) {
            List<Part> parts = fault.getMessage().getParts();
            if (parts.isEmpty()) continue;
            schema.addProperty(faultDetailKey(parts), convertParts(parts, visiting));
        }
        return schema;
    }

    /**
     * The key a fault's content appears under inside the detail element: the local name of its
     * wrapping XSD element, or the part name for RPC-style parts that only name a type.
     */
    static String faultDetailKey(List<Part> parts) {
        Part part = parts.getFirst();
        QName elementQName = part.getElementQName();
        return elementQName != null ? elementQName.getLocalPart() : part.getName();
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
                // A message's own schema is built inline even where the element names a type: a
                // request or response body that is nothing but a reference tells a reader nothing,
                // and the converter reads the body's fields to derive path and query parameters.
                return convertElementType(xsdElement, new XsdContext(schemaRoot, visiting), false);
            }
        }
        return new ObjectSchema();
    }

    /**
     * Resolves the Schema for an {@code <xsd:element>} node — its inline type
     * or the type referenced by the {@code type} attribute.
     * Does NOT apply maxOccurs wrapping; that is done by addElementField for sequence members.
     *
     * @param asRef whether a named type may be represented as a reference to its component; an
     *              inline type has no name to publish and is unaffected either way
     */
    private Schema<?> convertElementType(Element xsdElement, XsdContext ctx, boolean asRef) {
        return describedBy(xsdElement, resolveDeclaredType(xsdElement, ctx, asRef));
    }

    private Schema<?> resolveDeclaredType(Element xsdElement, XsdContext ctx, boolean asRef) {
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
            return resolveTypeRef(typeAttr, xsdElement, ctx, asRef);
        }
        return new ObjectSchema();
    }

    private Schema<?> buildObjectSchema(Element complexTypeEl, XsdContext ctx) {
        return describedBy(complexTypeEl, buildObjectSchemaContent(complexTypeEl, ctx));
    }

    /**
     * Carries a declaration's {@code xsd:documentation} over as the schema's {@code description}. A
     * description already on the schema — the documentation of the type an element names, or the note
     * a choice this converter cannot express leaves behind — is kept as a paragraph of its own, ahead
     * of which the more specific text goes.
     */
    private static <T extends Schema<?>> T describedBy(Element declaration, T schema) {
        String documentation = documentation(declaration);
        if (documentation == null) return schema;
        String existing = schema.getDescription();
        schema.setDescription(existing == null ? documentation : documentation + "\n\n" + existing);
        return schema;
    }

    private Schema<?> buildObjectSchemaContent(Element complexTypeEl, XsdContext ctx) {
        var objectSchema = new ObjectSchema();

        Element particle = firstParticle(complexTypeEl);
        if (particle != null) {
            addParticleFields(particle, objectSchema, ctx);
            addAttributeFields(complexTypeEl, objectSchema, ctx);
            return objectSchema;
        }
        Element complexContent = findXsdChild(complexTypeEl, "complexContent");
        if (complexContent != null) {
            Element extension = findXsdChild(complexContent, "extension");
            if (extension != null) {
                addBaseTypeFields(extension, objectSchema, ctx);
                addDeclaredFields(extension, objectSchema, ctx);
            }
            Element restriction = findXsdChild(complexContent, "restriction");
            if (restriction != null) {
                // a restriction re-declares the content model in full, so nothing is inherited
                addDeclaredFields(restriction, objectSchema, ctx);
            }
            return objectSchema;
        }
        Element simpleContent = findXsdChild(complexTypeEl, "simpleContent");
        if (simpleContent != null) {
            return buildSimpleContentSchema(simpleContent, ctx);
        }
        addAttributeFields(complexTypeEl, objectSchema, ctx);
        return objectSchema;
    }

    /**
     * Expands one {@code <xsd:sequence>}, {@code <xsd:all>}, {@code <xsd:choice>} or
     * {@code <xsd:group ref=.../>} particle into {@code schema}.
     */
    private void addParticleFields(Element particle, ObjectSchema schema, XsdContext ctx) {
        ContentNode node = contentModel.nodeOf(particle, ctx.schemaRoot(), ctx.visiting());
        if (node != null) addNode(node, schema, ctx);
    }

    /**
     * Writes one content model node into {@code schema}. A {@code sequence} or {@code all} adds its
     * children side by side, which is why a nested one is indistinguishable from its parent here; a
     * {@code choice} is the one particle that means something of its own — see
     * {@link #addChoiceFields}.
     *
     * <p>Each node names the schema document it came from, so a field a group in another document
     * contributed resolves its type against that document.
     */
    private void addNode(ContentNode node, ObjectSchema schema, XsdContext ctx) {
        switch (node) {
            case Field field -> addElementField(field.declaration(), schema, ctx.withRoot(field.schemaRoot()));
            case Container container when container.isChoice() -> addChoiceFields(container, schema, ctx);
            case Container container -> container.children().forEach(child -> addNode(child, schema, ctx));
        }
    }

    private void addElementField(Element el, ObjectSchema schema, XsdContext ctx) {
        String fieldName = el.getAttribute("name");
        if (fieldName.isEmpty()) {
            addRefField(el, schema, ctx);
            return;
        }
        addField(schema, fieldName, el, convertElementType(el, ctx, !mutates(el)));
    }

    /**
     * Resolves an {@code <xsd:element ref="prefix:local"/>} by looking up the referenced global
     * element and adding it as a property under its declared name.
     */
    private void addRefField(Element refEl, ObjectSchema schema, XsdContext ctx) {
        resolveRefAlternative(refEl, ctx, !mutates(refEl))
                .ifPresent(alternative -> addField(schema, alternative.localName(), refEl, alternative.fieldSchema()));
    }

    /**
     * Adds {@code fieldSchema} as a property, applying everything the declaration says about it —
     * see {@link #declaredSchema} — plus its minOccurs.
     */
    private static void addField(ObjectSchema schema, String fieldName, Element declaration, Schema<?> fieldSchema) {
        schema.addProperty(fieldName, declaredSchema(declaration, fieldSchema));
        if (!MIN_OCCURS_OPTIONAL.equals(declaration.getAttribute("minOccurs"))) {
            schema.addRequiredItem(fieldName);
        }
    }

    /**
     * The schema of an element declaration as the document publishes it: its type, made nullable
     * where the declaration is nillable, carrying its default or fixed value, and wrapped in an
     * array where it may occur more than once. Nullability and the value are applied before that
     * wrapping, because in XSD they describe each occurrence rather than the list.
     *
     * <p>Written onto {@code fieldSchema}, so the caller has to have built the type inline — see
     * {@link #mutates}.
     */
    private static Schema<?> declaredSchema(Element declaration, Schema<?> fieldSchema) {
        if ("true".equals(declaration.getAttribute("nillable"))) {
            makeNullable(fieldSchema);
        }
        applyDefaultValue(declaration, fieldSchema);
        return applyMaxOccurs(declaration, fieldSchema);
    }

    /**
     * Copies an {@code xsd:default} — or an {@code xsd:fixed}, the value the declaration pins the
     * field to — onto the schema, as a value of the field's own type: a numeric default is emitted
     * unquoted, and one that is no value of that type is left out rather than emitted as invalid.
     *
     * <p>An {@code xsd:fixed} additionally becomes a single-value {@code enum}: unlike a default, it
     * says the field may hold no other value, which is a constraint and belongs where validation
     * can see it. It carries the same typed value as the default, and is skipped along with it when
     * the literal does not fit the type.
     *
     * <p>Neither is used to fill in a field a message left out: the SOAP service applies its own
     * defaults, and a gateway that invented payload values would leave the backend unable to tell a
     * client's explicit choice from the gateway's guess.
     */
    @SuppressWarnings("unchecked")
    private static void applyDefaultValue(Element declaration, Schema<?> schema) {
        // XSD does not allow both on one declaration, so whichever is present is the value
        String fixed = declaration.getAttribute("fixed");
        String literal = fixed.isEmpty() ? declaration.getAttribute("default") : fixed;
        if (literal.isEmpty()) return;

        Object value = typedLiteral(schema, literal);
        if (value == null) return;

        var target = (Schema<Object>) schema;
        target.setDefault(value);
        if (!fixed.isEmpty()) {
            target.addEnumItemObject(value);
        }
    }

    /**
     * An XSD literal as the Java type the schema's own type calls for, or null when it is not a
     * value of that type at all — which is how a malformed literal costs the annotation or the
     * constraint it appears in rather than the whole conversion.
     *
     * <p>The typed Schema classes do cast a literal themselves, but along their own rules: a numeric
     * cast goes through a locale-dependent parse, and a boolean one reads every literal that is not
     * {@code "true"} — including the legal XSD spelling {@code 1} — as {@code false}.
     */
    private static Object typedLiteral(Schema<?> schema, String literal) {
        try {
            return switch (schema) {
                case BooleanSchema ignored -> xsdBoolean(literal);
                case IntegerSchema ignored -> integerLiteral(literal);
                case NumberSchema ignored -> new BigDecimal(literal);
                default -> literal;
            };
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** The four XSD spellings of a boolean; anything else is not one. */
    private static Boolean xsdBoolean(String literal) {
        return switch (literal) {
            case "true", "1" -> TRUE;
            case "false", "0" -> FALSE;
            default -> null;
        };
    }

    /** An integer literal as an int where one fits — xsd:integer is unbounded, so BigInteger otherwise. */
    private static Object integerLiteral(String literal) {
        var value = new BigInteger(literal);
        return value.bitLength() < 32 ? value.intValue() : value;
    }

    /**
     * Marks a schema as permitting no value at all. OpenAPI 3.1 — the version this converter emits
     * — says that by listing {@code "null"} among the type's allowed types; the 3.0 {@code nullable}
     * keyword is discarded by the 3.1 serializer, so setting it would lose the constraint. The
     * typed Schema classes already carry their own type in that list; the first branch only covers
     * a bare {@code Schema} that was given a type but no list.
     */
    private static void makeNullable(Schema<?> schema) {
        if (schema.getTypes() == null && schema.getType() != null) {
            schema.addType(schema.getType());
        }
        schema.addType(NULL_TYPE);
    }

    /** Wraps {@code fieldSchema} in an ArraySchema if the declaration allows more than one occurrence. */
    private static Schema<?> applyMaxOccurs(Element declaration, Schema<?> fieldSchema) {
        if (allowsMany(declaration.getAttribute("maxOccurs"))) {
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
            Schema<?> attributeSchema = convertElementType(el, ctx, !mutates(el));
            applyDefaultValue(el, attributeSchema);
            schema.addProperty(key, attributeSchema);
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

    /** One alternative of an {@code xsd:choice}: the keys it contributes, and those of them it requires. */
    private record ChoiceBranch(List<String> propertyKeys, List<String> requiredKeys) {
        static ChoiceBranch of(String key) {
            return new ChoiceBranch(List.of(key), List.of(key));
        }
    }

    /**
     * Maps choice alternatives to optional properties, plus a sibling {@code oneOf} whose branches
     * carry nothing but the {@code required} keys of one alternative each. The properties stay flat
     * — SOAP/JSON conversion looks types and array shapes up there — while the {@code oneOf} states
     * the constraint the choice actually expresses: a document that names two alternatives satisfies
     * two branches, and one that names none satisfies no branch, so both are rejected.
     *
     * <p>Where that constraint cannot be encoded — an optional or repeatable choice, or an
     * alternative whose own content is entirely optional and would therefore match anything — the
     * schema keeps the all-optional shape and states the constraint in its description instead.
     *
     * <p>Direct {@code xsd:element} alternatives are collected first so that same-local-name
     * collisions across namespaces can be detected and keyed with a namespace-qualified key
     * ({@link XsdDomUtil#qualifiedKey}) instead of silently overwriting each other.
     * Every other alternative — a nested particle or a group reference — is expanded by
     * {@link #addAlternativeFields}.
     */
    private void addChoiceFields(Container choice, ObjectSchema schema, XsdContext ctx) {
        var alternatives = new ArrayList<ChoiceAlternative>();
        var branches = new ArrayList<ChoiceBranch>();
        for (ContentNode child : choice.children()) {
            switch (child) {
                case Field field -> addChoiceAlternative(field, alternatives, ctx);
                case Container container ->
                        branches.add(addAlternativeFields(container, schema, ctx.withRoot(container.schemaRoot())));
            }
        }

        var colliding = collidingLocalNames(alternatives);
        for (var alt : alternatives) {
            boolean collides = colliding.contains(alt.localName()) && alt.namespaceURI() != null;
            String key = collides ? qualifiedKey(alt.namespaceURI(), alt.localName()) : alt.localName();
            schema.addProperty(key, alt.fieldSchema());
            branches.add(ChoiceBranch.of(key));
        }
        addExactlyOneConstraint(choice.declaration(), schema, branches);
    }

    /**
     * Collects one direct {@code xsd:element} alternative of a choice. Its schema is built inline
     * rather than as a reference where the declaration writes onto it, and the namespace it is
     * addressed under is recorded so that {@link #addChoiceFields} can tell a collision across
     * namespaces from a plain local name.
     */
    private void addChoiceAlternative(Field field, List<ChoiceAlternative> alternatives, XsdContext ctx) {
        Element el = field.declaration();
        XsdContext fieldCtx = ctx.withRoot(field.schemaRoot());
        String fieldName = el.getAttribute("name");
        if (fieldName.isEmpty()) {
            resolveRefAlternative(el, fieldCtx, !mutates(el))
                    .map(alternative -> alternative.withSchema(declaredSchema(el, alternative.fieldSchema())))
                    .ifPresent(alternatives::add);
            return;
        }
        String ns = field.schemaRoot().getAttribute("targetNamespace");
        alternatives.add(new ChoiceAlternative(fieldName, ns.isEmpty() ? null : ns,
                declaredSchema(el, convertElementType(el, fieldCtx, !mutates(el)))));
    }

    /**
     * Expands a choice alternative that is not a direct {@code xsd:element} — a nested particle or
     * an {@code xsd:group} reference — and merges its properties into {@code schema} <em>without</em>
     * its required list: any sibling alternative may be the one chosen at runtime, so nothing a
     * single branch declares can be globally required. The branch's own keys are returned so that
     * {@link #addChoiceFields} can require them when this alternative is the one chosen.
     */
    private ChoiceBranch addAlternativeFields(ContentNode particle, ObjectSchema schema, XsdContext ctx) {
        var branch = new ObjectSchema();
        addNode(particle, branch, ctx);
        if (branch.getProperties() == null) {
            return new ChoiceBranch(List.of(), List.of());
        }
        branch.getProperties().forEach(schema::addProperty);
        return new ChoiceBranch(List.copyOf(branch.getProperties().keySet()),
                branch.getRequired() == null ? List.of() : List.copyOf(branch.getRequired()));
    }

    /**
     * States that exactly one of {@code branches} is expected: as a {@code oneOf} of required-key-only
     * sub-schemas where that holds, and as a description where it does not — see
     * {@link #addChoiceFields}.
     */
    private static void addExactlyOneConstraint(Element choice, ObjectSchema schema, List<ChoiceBranch> branches) {
        if (branches.isEmpty()) return;

        if (allowsMany(choice.getAttribute("maxOccurs"))) {
            describeChoice(schema, "Repeatable choice: each occurrence is one of", branches);
            return;
        }
        if (MIN_OCCURS_OPTIONAL.equals(choice.getAttribute("minOccurs"))) {
            describeChoice(schema, "Optional choice: at most one of", branches);
            return;
        }
        if (branches.stream().anyMatch(branch -> branch.requiredKeys().isEmpty())) {
            describeChoice(schema, "Exactly one of", branches);
            return;
        }
        addOneOf(schema, branches.stream()
                .map(branch -> (Schema) new Schema<>().required(branch.requiredKeys()))
                .toList());
    }

    /**
     * Adds a {@code oneOf} without displacing one a sibling choice already put on the same schema:
     * two exclusive constraints on one content model both have to hold, which is an {@code allOf}.
     */
    @SuppressWarnings("rawtypes")
    private static void addOneOf(ObjectSchema schema, List<Schema> oneOf) {
        if (schema.getOneOf() == null && schema.getAllOf() == null) {
            schema.setOneOf(oneOf);
            return;
        }
        if (schema.getOneOf() != null) {
            List<Schema> previous = schema.getOneOf();
            schema.setOneOf(null);
            schema.addAllOfItem(new ComposedSchema().oneOf(previous));
        }
        schema.addAllOfItem(new ComposedSchema().oneOf(oneOf));
    }

    /** Names the alternatives of a choice whose constraint no keyword of the schema can carry. */
    private static void describeChoice(ObjectSchema schema, String lead, List<ChoiceBranch> branches) {
        if (schema.getDescription() != null) return;
        String alternatives = branches.stream()
                .filter(branch -> !branch.propertyKeys().isEmpty())
                .map(branch -> String.join(" + ", branch.propertyKeys()))
                .collect(Collectors.joining(", "));
        if (alternatives.isEmpty()) return;
        schema.setDescription("%s: %s. Not enforced by this schema.".formatted(lead, alternatives));
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
    private Optional<ChoiceAlternative> resolveRefAlternative(Element refEl, XsdContext ctx, boolean asRef) {
        return resolveElementRef(refEl, ctx.schemaRoot(), schemasByNamespace)
                .map(resolved -> new ChoiceAlternative(resolved.localName(), resolved.namespaceURI(),
                        convertElementType(resolved.declaration(), ctx.withRoot(resolved.schemaRoot()), asRef)));
    }

    /**
     * Merges the fields of an {@code <xsd:extension>}'s base type into {@code schema}, ahead of the
     * ones the extension declares itself. Only an extension inherits: an {@code <xsd:restriction>}
     * states its own content model completely, so a base field it leaves out is not part of the
     * derived type and must not appear.
     */
    private void addBaseTypeFields(Element extension, ObjectSchema schema, XsdContext ctx) {
        String base = extension.getAttribute("base");
        if (base.isEmpty()) return;

        // The base's fields are copied into the derived type, so the reference has to be followed:
        // resolving the base inline instead would yield nothing where a type contains the very type
        // that extends it — legal XSD, and the base is then already being expanded further up.
        Schema<?> baseSchema = dereference(resolveTypeRef(base, extension, ctx, true));
        if (baseSchema instanceof ObjectSchema baseObj && baseObj.getProperties() != null) {
            baseObj.getProperties().forEach(schema::addProperty);
            if (baseObj.getRequired() != null) {
                baseObj.getRequired().forEach(schema::addRequiredItem);
            }
        } else if (!(baseSchema instanceof ObjectSchema)) {
            log.debug("Base type '{}' is not an object schema, skipping field inheritance", base);
        }
    }

    /** The fields a derivation declares in its own right — its particle and its attributes. */
    private void addDeclaredFields(Element derivation, ObjectSchema schema, XsdContext ctx) {
        Element particle = firstParticle(derivation);
        if (particle != null) addParticleFields(particle, schema, ctx);
        addAttributeFields(derivation, schema, ctx);
    }

    /**
     * Builds the schema for an {@code <xsd:simpleContent>} type — an element carrying a text value
     * of its own plus, usually, attributes. Without attributes it is simply that value, so the
     * schema is the base type. With attributes the value cannot stand alone: it becomes the
     * {@code $value} property of an object that also holds the {@code @}-prefixed attributes.
     */
    private Schema<?> buildSimpleContentSchema(Element simpleContent, XsdContext ctx) {
        Element derivation = firstXsdChild(simpleContent, "extension", "restriction");
        if (derivation == null) return new StringSchema();

        // an xsd:restriction may narrow the value with facets; an xsd:extension carries none.
        // The facets are written onto the base's schema, so it is built inline rather than shared.
        Schema<?> valueSchema = resolveTypeRef(derivation.getAttribute("base"), derivation, ctx, false);
        applyFacets(derivation, valueSchema);

        var schema = new ObjectSchema();
        addAttributeFields(derivation, schema, ctx);
        if (schema.getProperties() == null) return valueSchema;

        schema.addProperty(VALUE_KEY, valueSchema);
        schema.addRequiredItem(VALUE_KEY);
        return schema;
    }

    private Schema<?> buildSimpleTypeSchema(Element simpleTypeEl, XsdContext ctx) {
        return describedBy(simpleTypeEl, buildSimpleTypeContent(simpleTypeEl, ctx));
    }

    private Schema<?> buildSimpleTypeContent(Element simpleTypeEl, XsdContext ctx) {
        Element restriction = findXsdChild(simpleTypeEl, "restriction");
        if (restriction == null) return new StringSchema();
        String base = restriction.getAttribute("base");
        // inline for the same reason as in buildSimpleContentSchema: applyFacets writes onto it
        Schema<?> schema = base.isEmpty() ? new StringSchema() : resolveTypeRef(base, restriction, ctx, false);
        applyFacets(restriction, schema);
        return schema;
    }

    /**
     * Applies the {@code <xsd:restriction>} facets that have a JSON Schema equivalent.
     * Facets without one ({@code totalDigits}, {@code fractionDigits}, {@code whiteSpace}) and
     * facet values that do not parse as numbers are ignored, so an unusable constraint costs the
     * constraint rather than the whole conversion.
     */
    @SuppressWarnings("unchecked")
    private void applyFacets(Element restriction, Schema<?> schema) {
        var patterns = new ArrayList<String>();
        for (Element el : xsdChildren(restriction)) {
            String value = el.getAttribute("value");
            switch (el.getLocalName()) {
                // A literal is typed like the schema it constrains: an enum of strings would match
                // no value of an integer or boolean field.
                case "enumeration" -> addEnumItem((Schema<Object>) schema, el, value);
                case "pattern"     -> patterns.add(value);
                case "minLength"   -> parseInteger(el, value).ifPresent(schema::setMinLength);
                case "maxLength"   -> parseInteger(el, value).ifPresent(schema::setMaxLength);
                case "length"      -> parseInteger(el, value).ifPresent(length -> {
                    schema.setMinLength(length);
                    schema.setMaxLength(length);
                });
                case "minInclusive" -> parseDecimal(el, value).ifPresent(schema::setMinimum);
                case "maxInclusive" -> parseDecimal(el, value).ifPresent(schema::setMaximum);
                // OpenAPI 3.1 carries an exclusive bound as its own numeric keyword, not as
                // minimum/maximum plus a boolean — that 3.0 form is dropped when serializing 3.1
                case "minExclusive" -> parseDecimal(el, value).ifPresent(schema::setExclusiveMinimumValue);
                case "maxExclusive" -> parseDecimal(el, value).ifPresent(schema::setExclusiveMaximumValue);
            }
        }
        if (!patterns.isEmpty()) schema.setPattern(jsonSchemaPattern(patterns));
    }

    private static void addEnumItem(Schema<Object> schema, Element facet, String literal) {
        Object value = typedLiteral(schema, literal);
        if (value == null) {
            log.debug("Ignoring xsd:enumeration value '{}': not a valid value of the type", literal);
            return;
        }
        schema.addEnumItemObject(value);
    }

    /**
     * The JSON Schema equivalent of a restriction's {@code pattern} facets. Two differences to XSD:
     * an XSD pattern must match the whole value while a JSON Schema one matches anywhere, hence the
     * anchors; and several pattern facets on one restriction are alternatives, while
     * {@code pattern} is a single expression, hence the alternation.
     *
     * <p>The expressions themselves are copied verbatim. XSD's regular expression dialect is not
     * ECMA's, so a pattern using a construct only XSD has stays as unusable as it was.
     */
    private static String jsonSchemaPattern(List<String> patterns) {
        return patterns.stream().collect(Collectors.joining("|", "^(?:", ")$"));
    }

    private static Optional<Integer> parseInteger(Element facet, String value) {
        try {
            return Optional.of(Integer.valueOf(value));
        } catch (NumberFormatException e) {
            log.debug("Ignoring xsd:{} facet with non-integer value '{}'", facet.getLocalName(), value);
            return Optional.empty();
        }
    }

    private static Optional<BigDecimal> parseDecimal(Element facet, String value) {
        try {
            return Optional.of(new BigDecimal(value));
        } catch (NumberFormatException e) {
            log.debug("Ignoring xsd:{} facet with non-numeric value '{}'", facet.getLocalName(), value);
            return Optional.empty();
        }
    }

    /**
     * Resolves a type reference string (e.g. {@code "tns:getBankType"}, {@code "xsd:string"})
     * to an OpenAPI schema. Uses the DOM context element for prefix→URI resolution so that
     * cross-namespace references are followed correctly.
     */
    private Schema<?> resolveTypeRef(String typeRef, Element contextElement, XsdContext ctx, boolean asRef) {
        if (typeRef.isEmpty()) return new StringSchema();
        String local = localName(typeRef);
        var found = findNamedType(
                resolveTargetSchemaRoots(prefix(typeRef), contextElement, ctx.schemaRoot(), schemasByNamespace), local);
        if (found.isEmpty()) return mapPrimitive(local);

        NamedType type = found.get();
        XsdContext typeCtx = ctx.withRoot(type.schemaRoot());
        Supplier<Schema<?>> build = switch (type.kind()) {
            case COMPLEX -> () -> buildObjectSchema(type.element(), typeCtx);
            case SIMPLE -> () -> buildSimpleTypeSchema(type.element(), typeCtx);
        };
        if (asRef) {
            return componentRef(targetNamespace(type.schemaRoot()), local, build);
        }
        // Only a complexType needs the cycle guard: a simpleType cannot expand into itself.
        return type.kind() == Kind.COMPLEX ? buildInline(type.element(), local, build, ctx) : build.get();
    }

    /**
     * The complexType or simpleType named {@code localName} among {@code roots}, and the document it
     * was found in. Every document is searched for a complexType before any is searched for a
     * simpleType — the two share a symbol space, so no valid schema declares both under one name.
     */
    private static Optional<NamedType> findNamedType(List<Element> roots, String localName) {
        for (var root : roots) {
            Element complexType = findXsdChildWithName(root, "complexType", localName);
            if (complexType != null) return Optional.of(new NamedType(complexType, root, Kind.COMPLEX));
        }
        for (var root : roots) {
            Element simpleType = findXsdChildWithName(root, "simpleType", localName);
            if (simpleType != null) return Optional.of(new NamedType(simpleType, root, Kind.SIMPLE));
        }
        return Optional.empty();
    }

    /**
     * Builds a named complexType in place rather than as a reference, for the use sites that go on to
     * write onto the schema they are given. Recursion has no reference to break the cycle with here,
     * so a type reached while it is already being expanded yields an empty schema — which a use site
     * can only run into by being both self-referential and one of those few sites.
     */
    private Schema<?> buildInline(Element complexType, String local, Supplier<Schema<?>> build, XsdContext ctx) {
        if (ctx.visiting().contains(complexType)) {
            log.debug("Recursive reference to type '{}' from a declaration that modifies it, returning empty schema", local);
            return new ObjectSchema();
        }
        ctx.visiting().add(complexType);
        try {
            return build.get();
        } finally {
            ctx.visiting().remove(complexType);
        }
    }

    private static String targetNamespace(Element schemaRoot) {
        return schemaRoot.getAttribute("targetNamespace");
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
        String local = qname.getLocalPart();
        if (XSD_NS.equals(qname.getNamespaceURI())) return mapPrimitive(local);
        List<Element> targetRoots = schemasByNamespace.get(qname.getNamespaceURI());
        if (targetRoots == null) return mapPrimitive(local);
        return findNamedType(targetRoots, local)
                .map(type -> switch (type.kind()) {
                    case COMPLEX -> buildObjectSchema(type.element(), new XsdContext(type.schemaRoot(), visiting));
                    case SIMPLE -> buildSimpleTypeSchema(type.element(), new XsdContext(type.schemaRoot(), visiting));
                })
                .orElseGet(() -> mapPrimitive(local));
    }

    private Schema<?> mapPrimitive(String localPart) {
        return switch (localPart) {
            case "string" -> new StringSchema();
            case "date" -> withFormat(new StringSchema(), "date");
            case "dateTime" -> withFormat(new StringSchema(), "date-time");
            case "base64Binary" -> withFormat(new StringSchema(), "byte");
            case "hexBinary" -> withFormat(new StringSchema(), "binary");
            case "anyURI" -> withXsdType(withFormat(new StringSchema(), "uri"), localPart);
            case "time" -> withXsdType(withFormat(new StringSchema(), "time"), localPart);
            case "duration" -> withXsdType(withFormat(new StringSchema(), "duration"), localPart);
            case "normalizedString", "token", "language",
                 "gYear", "gMonth", "gDay", "gYearMonth", "gMonthDay",
                 "QName", "NOTATION" -> withXsdType(new StringSchema(), localPart);
            case "int" -> withFormat(new IntegerSchema(), "int32");
            case "long" -> withFormat(new IntegerSchema(), "int64");
            case "integer", "short", "byte",
                 "nonNegativeInteger", "positiveInteger",
                 "nonPositiveInteger", "negativeInteger",
                 "unsignedInt", "unsignedShort", "unsignedByte", "unsignedLong" -> withXsdType(withFormat(new IntegerSchema(), null), localPart);
            case "float" -> withFormat(new NumberSchema(), "float");
            case "double" -> withFormat(new NumberSchema(), "double");
            case "decimal" -> withXsdType(withFormat(new NumberSchema(), "decimal"), localPart);
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

    /**
     * Records the XSD type a schema came from, for the types whose {@code type}/{@code format} pair
     * does not name them: an {@code x-xsd-type} extension a tool can read, rather than prose in
     * {@code description} — which belongs to the service's own documentation.
     */
    private static <T extends Schema<?>> T withXsdType(T schema, String xsdType) {
        schema.addExtension(XSD_TYPE_EXTENSION, xsdType);
        return schema;
    }


    /**
     * Whether a declaration permits more than one occurrence — XSD says so either with
     * {@code maxOccurs="unbounded"} or with an integer above one. Absent, empty or unparseable counts
     * as a single occurrence, which is the XSD default.
     */
    private static boolean allowsMany(String maxOccurs) {
        if (UNBOUNDED.equals(maxOccurs)) return true;
        if (maxOccurs == null || maxOccurs.isEmpty()) return false;
        try {
            return Integer.parseInt(maxOccurs) > 1;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
