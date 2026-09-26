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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.core.util.wsdl.parser.Definitions;
import com.predic8.membrane.core.util.wsdl.parser.Message;
import com.predic8.membrane.core.util.wsdl.parser.Operation;
import com.predic8.membrane.core.util.wsdl.parser.Part;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayOutputStream;
import java.util.*;

import static com.predic8.membrane.annot.Constants.SOAP11_NS;
import static com.predic8.membrane.annot.Constants.SOAP12_NS;
import static com.predic8.membrane.core.interceptor.wsdl2openapi.XsdContentModel.*;
import static com.predic8.membrane.core.interceptor.wsdl2openapi.XsdDomUtil.*;
import static com.predic8.membrane.core.util.wsdl.parser.Definitions.SOAPVersion.SOAP_11;
import static com.predic8.membrane.core.util.wsdl.parser.Definitions.SOAPVersion.SOAP_12;
import static com.predic8.membrane.core.util.wsdl.parser.Operation.Direction.INPUT;

/**
 * Transforms JSON request to SOAP XML envelope.
 * JSON keys prefixed with "@" are mapped to XML attributes instead of child elements.
 * A {@code null} value becomes an element marked {@code xsi:nil="true"}, or, for an attribute,
 * no attribute at all.
 * A {@code $value} key supplies the enclosing element's own text, for an element that carries both
 * a value and attributes.
 *
 * <p>One instance serves every concurrent exchange of its operation, so everything taken from the
 * WSDL is resolved once, in the constructor, and {@link #transform} only reads the immutable result.
 * The WSDL and its schemas are a Xerces DOM, which is not thread-safe even for reads: walking it
 * per request raced on its node-list cache and left the definitions corrupted for later requests.
 */
public class Json2SoapTransformer {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Looked up once: {@code newInstance()} scans the classpath for a provider under a JVM-wide
     * class-loader lock, which serialized all request threads. Builders and transformers are not
     * thread-safe and are still created per request.
     */
    private static final DocumentBuilderFactory DOCUMENT_BUILDER_FACTORY = createDocumentBuilderFactory();
    private static final TransformerFactory TRANSFORMER_FACTORY = TransformerFactory.newInstance();

    private final String operationName;
    private final Map<String, List<Element>> schemasByNamespace;
    private final XsdContentModel contentModel;
    private final boolean soap12;
    /** The operation's request element, or {@code null} when the WSDL does not define a usable one. */
    private final RequestElement requestElement;
    /** Why the operation has no usable request element, reported by every {@link #transform} call. */
    private final String unusableReason;
    /** The field metadata of every declaration reachable from the request element. */
    private final Map<XsdContext, FieldContext> fieldContexts;

    /**
     * @param schemasByNamespace the import graph resolved once by the caller — see
     *                           {@link XsdToSchema#getSchemasByNamespace()}. One transformer is built
     *                           per operation, so deriving it here would walk the graph once per
     *                           operation instead of once per API.
     */
    public Json2SoapTransformer(Definitions definitions, String operationName,
                                Map<String, List<Element>> schemasByNamespace) {
        this.operationName = operationName;
        this.schemasByNamespace = schemasByNamespace;
        this.contentModel = new XsdContentModel(schemasByNamespace);
        this.soap12 = useSoap12(definitions);

        var contexts = new HashMap<XsdContext, FieldContext>();
        RequestElement resolved = null;
        String reason = null;
        try {
            resolved = resolveRequestElement(definitions, contexts);
        } catch (IllegalArgumentException e) {
            // Reported per request, as before: one unusable operation must not stop the others.
            reason = e.getMessage();
        }
        this.requestElement = resolved;
        this.unusableReason = reason;
        this.fieldContexts = Map.copyOf(contexts);
    }

    public byte[] transform(String jsonBody) throws Exception {
        JsonNode jsonNode = MAPPER.readTree(jsonBody);

        if (requestElement == null) {
            throw new IllegalArgumentException(unusableReason);
        }

        Envelope envelope = createSoapEnvelope();
        Document doc = envelope.doc();

        Element operationElement = createOperationElement(doc);
        envelope.body().appendChild(operationElement);

        mapJsonToElement(jsonNode, operationElement, doc, requestElement.fields());

        return documentToBytes(doc);
    }

    /** The element a request's SOAP body carries, and the field metadata of its content. */
    private record RequestElement(String name, String namespace, FieldContext fields) {}

    /**
     * Resolves the operation's request element from the WSDL, collecting the field metadata of every
     * declaration reachable from it into {@code contexts}.
     *
     * @throws IllegalArgumentException if the operation, its input message, or the element of its
     *                                  first part cannot be found
     */
    private RequestElement resolveRequestElement(Definitions definitions, Map<XsdContext, FieldContext> contexts) {
        Operation operation = definitions.findOperation(operationName)
                .orElseThrow(() -> new IllegalArgumentException("Operation not found: " + operationName));

        List<Message> inputMessages = operation.getMessagesByDirection(INPUT);
        if (inputMessages.isEmpty()) {
            throw new IllegalArgumentException("No input message found for operation: " + operationName);
        }
        List<Part> parts = inputMessages.getFirst().getParts();
        if (parts.isEmpty()) {
            throw new IllegalArgumentException("Input message has no parts for operation: " + operationName);
        }
        Part part = parts.getFirst();
        String elementName = part.getElementName();
        if (elementName == null) {
            throw new IllegalArgumentException("Part has no element name for operation: " + operationName);
        }
        return new RequestElement(elementName, part.getElementNamespace(),
                resolveFieldContexts(findXsdContext(part.getElementQName()), contexts));
    }

    /**
     * The field metadata of {@code root}, after resolving it for every declaration reachable from
     * it into {@code contexts}. Keyed by declaration, so a recursive type is resolved once and ends
     * the walk instead of repeating it.
     */
    private FieldContext resolveFieldContexts(XsdContext root, Map<XsdContext, FieldContext> contexts) {
        if (root == null) return NO_FIELD_CONTEXT;
        var pending = new ArrayDeque<XsdContext>();
        pending.push(root);
        while (!pending.isEmpty()) {
            XsdContext context = pending.pop();
            if (contexts.containsKey(context)) continue;
            FieldContext fieldContext = fieldContextFor(context);
            contexts.put(context, fieldContext);
            fieldContext.childrenByLocalName().values().forEach(pending::push);
        }
        return contexts.get(root);
    }

    /** The field metadata of a child declaration; every one was resolved in the constructor. */
    private FieldContext fieldContextOf(XsdContext context) {
        return context == null ? NO_FIELD_CONTEXT : fieldContexts.get(context);
    }

    /** A freshly created SOAP envelope document, together with its Body element. */
    private record Envelope(Document doc, Element body) {}

    private static DocumentBuilderFactory createDocumentBuilderFactory() {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory;
    }

    private Envelope createSoapEnvelope() throws Exception {
        DocumentBuilder builder = DOCUMENT_BUILDER_FACTORY.newDocumentBuilder();
        Document doc = builder.newDocument();

        String soapNs = soap12 ? SOAP12_NS : SOAP11_NS;
        String prefix = soap12 ? "s12" : "s11";

        Element envelope = doc.createElementNS(soapNs, prefix + ":Envelope");
        envelope.setAttribute("xmlns:" + prefix, soapNs);
        doc.appendChild(envelope);

        Element body = doc.createElementNS(soapNs, prefix + ":Body");
        envelope.appendChild(body);

        return new Envelope(doc, body);
    }

    private static boolean useSoap12(Definitions definitions) {
        Set<Definitions.SOAPVersion> versions = definitions.getSoapVersions();
        return versions.contains(SOAP_12) && !versions.contains(SOAP_11);
    }

    private Element createOperationElement(Document doc) {
        String namespace = requestElement.namespace();

        Element opElement = doc.createElementNS(namespace, requestElement.name());

        if (namespace != null && !namespace.isEmpty()) {
            String prefix = "ns";
            opElement.setPrefix(prefix);
            opElement.setAttribute("xmlns:" + prefix, namespace);
        }

        return opElement;
    }

    /**
     * Every {@code <xsd:element>} declaration in the type's content model, in the order an instance
     * must present them: the particle structure {@link XsdContentModel} resolved, flattened — a
     * choice is no different here, since the JSON decides which alternative is actually written.
     *
     * <p>What this class adds to that walk is inheritance: an {@code xsd:extension} contributes its
     * base type's fields ahead of the derived ones, while an {@code xsd:restriction} inherits
     * nothing because it re-declares the whole model. {@link XsdToSchema} inherits the base's
     * <em>schema</em> instead of re-walking its declarations, which is why that step is not shared.
     *
     * <p>Each declaration travels with the schema document it was found in, because a base type or
     * group may live in another document whose {@code elementFormDefault} and {@code targetNamespace}
     * decide the namespace its locally-declared fields carry.
     */
    private List<Field> contentModelFields(ResolvedType type) {
        var fields = new ArrayList<Field>();
        collectComplexTypeFields(type, fields, new HashSet<>());
        return fields;
    }

    private void collectComplexTypeFields(ResolvedType type, List<Field> fields, Set<Element> visiting) {
        if (type == null || !visiting.add(type.complexType())) return;
        try {
            // only an xsd:extension inherits — an xsd:restriction states its content model in full
            Element derivation = derivationOf(type.complexType());
            if (derivation != null && "extension".equals(derivation.getLocalName())) {
                collectComplexTypeFields(baseTypeOf(derivation, type.schemaRoot()), fields, visiting);
            }
            Element particle = firstParticle(derivation != null ? derivation : type.complexType());
            if (particle == null) return;
            ContentNode node = contentModel.nodeOf(particle, type.schemaRoot(), visiting);
            // A choice contributes every alternative: which one a request carries is the client's
            // choice, and each has to be writable when it is the one present.
            if (node != null) fields.addAll(fieldsOf(node));
        } finally {
            visiting.remove(type.complexType());
        }
    }

    /** The {@code xsd:extension} or {@code xsd:restriction} of a derived type, or {@code null} if it is not derived. */
    private static Element derivationOf(Element complexType) {
        Element complexContent = findXsdChild(complexType, "complexContent");
        if (complexContent == null) return null;
        Element extension = findXsdChild(complexContent, "extension");
        return extension != null ? extension : findXsdChild(complexContent, "restriction");
    }

    /** The complexType a derivation's {@code base=} points at, or {@code null} if it cannot be resolved. */
    private ResolvedType baseTypeOf(Element derivation, Element schemaRoot) {
        String base = derivation.getAttribute("base");
        return base.isEmpty() ? null : resolveComplexType(base, derivation, schemaRoot);
    }

    /**
     * The complexType defining the content of the context's xsd:element — either declared inline
     * or referenced by its {@code type=} attribute. Returns {@code null} if there is none.
     */
    private ResolvedType complexTypeOf(XsdContext context) {
        Element inline = findXsdChild(context.xsdElement(), "complexType");
        if (inline != null) return new ResolvedType(inline, context.schemaRoot());
        String typeAttr = context.xsdElement().getAttribute("type");
        if (typeAttr.isEmpty()) return null;
        return resolveComplexType(typeAttr, context.xsdElement(), context.schemaRoot());
    }

    /**
     * Resolves a {@code type="prefix:local"} reference to a named {@code xsd:complexType} element.
     */
    private ResolvedType resolveComplexType(String typeRef, Element contextElement, Element currentSchemaRoot) {
        String prefix = prefix(typeRef);
        String local = localName(typeRef);
        List<Element> targetRoots = resolveTargetSchemaRoots(prefix, contextElement, currentSchemaRoot, schemasByNamespace);
        for (var root : targetRoots) {
            Element complexType = findXsdChildWithName(root, "complexType", local);
            if (complexType != null) return new ResolvedType(complexType, root);
        }
        return null;
    }

    /**
     * The namespace each field carries, keyed as in {@link #bindFields}. Fields with no namespace —
     * locally declared in a schema that is not {@code elementFormDefault="qualified"} — are absent,
     * so that {@code makeElement} creates them without one.
     */
    private static Map<String, String> fieldNamespacesOf(List<FieldBinding> bindings) {
        var result = new LinkedHashMap<String, String>();
        for (var binding : bindings) {
            if (binding.namespaceURI() != null) result.put(binding.key(), binding.namespaceURI());
        }
        return result;
    }

    /** The namespace locally-declared fields carry — only set when {@code elementFormDefault="qualified"}. */
    private static String defaultNamespace(Element schemaRoot) {
        return "qualified".equals(schemaRoot.getAttribute("elementFormDefault"))
                ? schemaRoot.getAttribute("targetNamespace") : null;
    }

    /** A field name/namespace pair, resolved prior to deciding its final (possibly-qualified) key. */
    private record FieldRef(String localName, String namespaceURI) {}

    /** A content-model field: the JSON key addressing it, and the namespace it carries ({@code null} = none). */
    private record FieldBinding(String key, String namespaceURI) {}

    /** A resolved {@code xsd:complexType} and the schema document it was found in. */
    private record ResolvedType(Element complexType, Element schemaRoot) {}

    /** The XSD element declaration and its containing schema root, used to resolve child field metadata. */
    private record XsdContext(Element xsdElement, Element schemaRoot) {}

    /**
     * Returns the XSD element declaration and schema root for the given element QName,
     * or {@code null} if not found.
     */
    private XsdContext findXsdContext(QName elementQName) {
        if (elementQName == null) return null;
        List<Element> roots = schemasByNamespace.get(elementQName.getNamespaceURI());
        if (roots == null) return null;
        for (var root : roots) {
            Element el = findXsdChildWithName(root, "element", elementQName.getLocalPart());
            if (el != null) return new XsdContext(el, root);
        }
        return null;
    }

    /**
     * The declaration of each child in the complexType sequence/all/choice of a type, by the XML
     * local name it appears under — taken from {@code name=}, or resolved through {@code ref=} —
     * together with the schema document it was found in. The document matters: a referenced
     * element's own {@code type=} references resolve against <em>its</em> schema, not the referrer's.
     *
     * <p>Built once per object from the content model its field metadata was already derived from,
     * rather than searched per field. Where two namespaces contribute the same local name the first
     * declaration wins, as it did when this was a sequential search.
     */
    private Map<String, XsdContext> indexChildren(List<Field> fields) {
        var byLocalName = new LinkedHashMap<String, XsdContext>();
        for (var field : fields) {
            Element el = field.declaration();
            String name = el.getAttribute("name");
            if (!name.isEmpty()) {
                byLocalName.putIfAbsent(name, new XsdContext(el, field.schemaRoot()));
                continue;
            }
            // a ref= child: the declaration lives in the schema the ref points at
            String ref = el.getAttribute("ref");
            if (ref.isEmpty()) continue;
            resolveElementRef(el, field.schemaRoot(), schemasByNamespace).ifPresent(resolved ->
                    byLocalName.putIfAbsent(localName(ref),
                            new XsdContext(resolved.declaration(), resolved.schemaRoot())));
        }
        return byLocalName;
    }

    /**
     * Binds every declared field — locally declared and {@code ref}'d alike — to the JSON key that
     * addresses it, keeping content-model order. Fields whose local name collides across
     * namespaces (e.g. two {@code ref}'d elements both named {@code value}, from different
     * namespaces) are keyed with a namespace-qualified key ({@link XsdDomUtil#qualifiedKey})
     * instead of silently overwriting each other — mirrors {@code XsdToSchema.addChoiceFields}.
     */
    private List<FieldBinding> bindFields(List<Field> fields) {
        var refs = new ArrayList<FieldRef>();
        for (var field : fields) {
            FieldRef ref = fieldRefOf(field);
            if (ref != null) refs.add(ref);
        }

        var occurrences = new HashMap<String, Integer>();
        for (var r : refs) {
            occurrences.merge(r.localName(), 1, Integer::sum);
        }
        var bindings = new ArrayList<FieldBinding>();
        for (var r : refs) {
            boolean collides = occurrences.get(r.localName()) > 1 && r.namespaceURI() != null;
            bindings.add(new FieldBinding(
                    collides ? qualifiedKey(r.namespaceURI(), r.localName()) : r.localName(),
                    r.namespaceURI()));
        }
        return bindings;
    }

    /**
     * The name and namespace one declaration contributes, or {@code null} if it declares neither a
     * {@code name=} nor a resolvable {@code ref=}. The namespace is derived from the declaration's
     * own schema document, so an inherited or grouped-in field follows the
     * {@code elementFormDefault} of the document declaring it rather than the referring one.
     */
    private static FieldRef fieldRefOf(Field field) {
        Element el = field.declaration();
        String name = el.getAttribute("name");
        if (!name.isEmpty()) {
            // locally declared: namespaced only when its own schema is elementFormDefault="qualified"
            return new FieldRef(name, defaultNamespace(field.schemaRoot()));
        }
        String ref = el.getAttribute("ref");
        if (ref.isEmpty()) return null;
        // Resolved by name alone, not through resolveElementRef: a field must keep its place in the
        // content model even where the referenced declaration itself is outside the import graph.
        String refNs = referencedNamespace(ref, el, field.schemaRoot());
        return refNs == null || refNs.isEmpty() ? null : new FieldRef(localName(ref), refNs);
    }

    /**
     * The WSDL-derived metadata for the element currently being written: the order its fields are
     * declared in, the namespace each field carries, and the XSD declaration of each child by local
     * name (which is what a nested object needs to derive the same values for itself).
     */
    private record FieldContext(List<String> fieldOrder, Map<String, String> fieldNamespaces,
                                Map<String, XsdContext> childrenByLocalName) {}

    private static final FieldContext NO_FIELD_CONTEXT = new FieldContext(List.of(), Map.of(), Map.of());

    /**
     * The field metadata of {@code context}, or an empty context when the XSD declaration is unknown.
     * The content model is resolved once here and both the field bindings and the child index are
     * derived from it. Walks the schema DOM, so it runs only from the constructor — see
     * {@link #resolveFieldContexts}.
     */
    private FieldContext fieldContextFor(XsdContext context) {
        if (context == null) return NO_FIELD_CONTEXT;
        List<Field> fields = contentModelFields(complexTypeOf(context));
        List<FieldBinding> bindings = bindFields(fields);
        return new FieldContext(bindings.stream().map(FieldBinding::key).toList(),
                fieldNamespacesOf(bindings), indexChildren(fields));
    }

    private void mapJsonToElement(JsonNode jsonNode, Element parent, Document doc, FieldContext context) {
        if (jsonNode.isObject()) {
            var emitted = new LinkedHashSet<String>();
            for (String name : context.fieldOrder()) {
                JsonNode val = jsonNode.get(name);
                if (val != null) {
                    emitField(name, val, parent, doc, context);
                    emitted.add(name);
                }
            }
            for (var entry : jsonNode.properties()) {
                if (!emitted.contains(entry.getKey())) {
                    emitField(entry.getKey(), entry.getValue(), parent, doc, context);
                }
            }
        } else if (jsonNode.isValueNode()) {
            setLeafValue(parent, jsonNode);
        }
    }

    private void emitField(String fieldName, JsonNode fieldValue, Element parent, Document doc, FieldContext context) {
        if (VALUE_KEY.equals(fieldName)) {
            // the element's own text, sitting alongside its attributes — an xsd:simpleContent type
            setLeafValue(parent, fieldValue);
            return;
        }
        if (fieldName.startsWith(ATTRIBUTE_PREFIX)) {
            // An XML attribute has no way to say "no value" — a null is an absent attribute
            if (!fieldValue.isNull()) {
                parent.setAttribute(fieldName.substring(ATTRIBUTE_PREFIX.length()), fieldValue.asText());
            }
            return;
        }
        String ns = context.fieldNamespaces().get(fieldName);
        String xmlLocalName = localNameFromKey(fieldName);
        FieldContext childContext = fieldContextOf(context.childrenByLocalName().get(xmlLocalName));

        if (fieldValue.isArray()) {
            for (JsonNode arrayItem : fieldValue) {
                Element arrayElement = makeElement(doc, ns, xmlLocalName);
                if (arrayItem.isValueNode()) {
                    setLeafValue(arrayElement, arrayItem);
                } else {
                    mapJsonToElement(arrayItem, arrayElement, doc, childContext);
                }
                parent.appendChild(arrayElement);
            }
        } else {
            Element childElement = makeElement(doc, ns, xmlLocalName);
            parent.appendChild(childElement);
            if (fieldValue.isObject()) {
                mapJsonToElement(fieldValue, childElement, doc, childContext);
            } else {
                setLeafValue(childElement, fieldValue);
            }
        }
    }

    /**
     * Writes a JSON scalar as the element's value. A JSON {@code null} becomes an empty element
     * marked {@code xsi:nil="true"} — the XML way of saying "present but no value" — rather than
     * the text "null", which a service would read as an ordinary string.
     */
    private static void setLeafValue(Element element, JsonNode value) {
        if (value.isNull()) {
            element.setAttributeNS(XSI_NS, "xsi:" + NIL_ATTRIBUTE, "true");
            return;
        }
        // Appended, not set: setTextContent would drop child elements an earlier key already
        // produced, making the result depend on the order the JSON happens to list its keys in.
        element.appendChild(element.getOwnerDocument().createTextNode(value.asText()));
    }

    private static Element makeElement(Document doc, String namespace, String name) {
        return namespace != null ? doc.createElementNS(namespace, name) : doc.createElement(name);
    }

    private byte[] documentToBytes(Document doc) throws Exception {
        Transformer transformer = TRANSFORMER_FACTORY.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "no");
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        transformer.transform(new DOMSource(doc), new StreamResult(outputStream));
        return outputStream.toByteArray();
    }

}
