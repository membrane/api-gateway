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
import static com.predic8.membrane.core.interceptor.wsdl2openapi.XsdDomUtil.*;
import static com.predic8.membrane.core.util.wsdl.parser.Definitions.SOAPVersion.SOAP_11;
import static com.predic8.membrane.core.util.wsdl.parser.Definitions.SOAPVersion.SOAP_12;
import static com.predic8.membrane.core.util.wsdl.parser.Operation.Direction.INPUT;

/**
 * Transforms JSON request to SOAP XML envelope.
 * JSON keys prefixed with "@" are mapped to XML attributes instead of child elements.
 */
public class Json2SoapTransformer {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Definitions definitions;
    private final String operationName;
    private final Map<String, List<Element>> schemasByNamespace;

    public Json2SoapTransformer(Definitions definitions, String operationName) {
        this.definitions = definitions;
        this.operationName = operationName;
        this.schemasByNamespace = buildSchemaMap(definitions);
    }

    public byte[] transform(String jsonBody) throws Exception {
        JsonNode jsonNode = MAPPER.readTree(jsonBody);

        List<Message> inputMessages = findOperation(operationName).getMessagesByDirection(INPUT);
        if (inputMessages.isEmpty()) {
            throw new IllegalArgumentException("No input message found for operation: " + operationName);
        }
        Message inputMessage = inputMessages.getFirst();

        Envelope envelope = createSoapEnvelope();
        Document doc = envelope.doc();

        Element operationElement = createOperationElement(doc, inputMessage);
        envelope.body().appendChild(operationElement);

        var partQName = inputMessage.getParts().getFirst().getElementQName();
        mapJsonToElement(jsonNode, operationElement, doc, fieldContextFor(findXsdContext(partQName)));

        return documentToBytes(doc);
    }

    private Operation findOperation(String name) {
        return definitions.getPortTypes().stream()
                .flatMap(pt -> pt.getOperations().stream())
                .filter(op -> name.equals(op.getName()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Operation not found: " + name));
    }

    /** A freshly created SOAP envelope document, together with its Body element. */
    private record Envelope(Document doc, Element body) {}

    private Envelope createSoapEnvelope() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.newDocument();

        String soapNs = useSoap12() ? SOAP12_NS : SOAP11_NS;
        String prefix = useSoap12() ? "s12" : "s11";

        Element envelope = doc.createElementNS(soapNs, prefix + ":Envelope");
        envelope.setAttribute("xmlns:" + prefix, soapNs);
        doc.appendChild(envelope);

        Element body = doc.createElementNS(soapNs, prefix + ":Body");
        envelope.appendChild(body);

        return new Envelope(doc, body);
    }

    private boolean useSoap12() {
        Set<Definitions.SOAPVersion> versions = definitions.getSoapVersions();
        return versions.contains(SOAP_12) && !versions.contains(SOAP_11);
    }

    private Element createOperationElement(Document doc, Message message) {
        if (message.getParts().isEmpty()) {
            throw new IllegalArgumentException("Input message has no parts for operation: " + operationName);
        }
        Part part = message.getParts().getFirst();
        String elementName = part.getElementName();
        if (elementName == null) {
            throw new IllegalArgumentException("Part has no element name for operation: " + operationName);
        }
        String namespace = part.getElementNamespace();

        Element opElement = doc.createElementNS(namespace, elementName);

        if (namespace != null && !namespace.isEmpty()) {
            String prefix = "ns";
            opElement.setPrefix(prefix);
            opElement.setAttribute("xmlns:" + prefix, namespace);
        }

        return opElement;
    }

    /**
     * The fields of the context's complexType, in declaration order — both locally declared
     * ({@code name=}) and referenced ({@code ref=}) children. Handles inline complexTypes and
     * {@code type=} references to named complexTypes.
     */
    private List<FieldBinding> extractFieldBindings(XsdContext context) {
        if (context == null) return List.of();
        Element complexType = complexTypeOf(context);
        if (complexType == null) return List.of();

        Element container = firstXsdChild(complexType, "sequence", "all", "choice");
        if (container == null) return List.of();

        return bindFields(container, context.schemaRoot(), defaultNamespace(context.schemaRoot()));
    }

    /**
     * The complexType defining the content of the context's xsd:element — either declared inline
     * or referenced by its {@code type=} attribute. Returns {@code null} if there is none.
     */
    private Element complexTypeOf(XsdContext context) {
        Element inline = findXsdChild(context.xsdElement(), "complexType");
        if (inline != null) return inline;
        String typeAttr = context.xsdElement().getAttribute("type");
        if (typeAttr.isEmpty()) return null;
        return resolveComplexType(typeAttr, context.xsdElement(), context.schemaRoot());
    }

    /**
     * Resolves a {@code type="prefix:local"} reference to a named {@code xsd:complexType} element.
     */
    private Element resolveComplexType(String typeRef, Element contextElement, Element currentSchemaRoot) {
        String prefix = prefix(typeRef);
        String local = localName(typeRef);
        List<Element> targetRoots = resolveTargetSchemaRoots(prefix, contextElement, currentSchemaRoot, schemasByNamespace);
        for (var root : targetRoots) {
            Element complexType = findXsdChildWithName(root, "complexType", local);
            if (complexType != null) return complexType;
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
     * The declaration of the named child in the complexType sequence/all/choice of
     * {@code context} — matched on {@code name=}, or resolved through {@code ref=} — together with
     * the schema document it was found in. The document matters: a referenced element's own
     * {@code type=} references resolve against <em>its</em> schema, not the referrer's.
     * Returns {@code null} if the parent has no complexType or no matching child.
     */
    private XsdContext findChildXsdContext(XsdContext context, String childLocalName) {
        Element complexType = complexTypeOf(context);
        if (complexType == null) return null;
        Element container = firstXsdChild(complexType, "sequence", "all", "choice");
        if (container == null) return null;

        Element declared = findXsdChildWithName(container, "element", childLocalName);
        if (declared != null) return new XsdContext(declared, context.schemaRoot());

        return resolveReferencedChild(container, childLocalName, context.schemaRoot());
    }

    /**
     * Resolves an {@code <xsd:element ref="prefix:local"/>} child whose local name is
     * {@code childLocalName} to the global element it points at, in the schema that declares it.
     */
    private XsdContext resolveReferencedChild(Element container, String childLocalName, Element schemaRoot) {
        for (Element el : xsdChildren(container)) {
            if (!"element".equals(el.getLocalName())) continue;
            String ref = el.getAttribute("ref");
            if (ref.isEmpty() || !childLocalName.equals(localName(ref))) continue;

            for (var root : resolveTargetSchemaRoots(prefix(ref), el, schemaRoot, schemasByNamespace)) {
                Element referenced = findXsdChildWithName(root, "element", childLocalName);
                if (referenced != null) return new XsdContext(referenced, root);
            }
        }
        return null;
    }

    /**
     * Binds every field in {@code container} — locally declared and {@code ref}'d alike — to the
     * JSON key that addresses it, in declaration order. Fields whose local name collides across
     * namespaces (e.g. two {@code ref}'d elements both named {@code value}, from different
     * namespaces) are keyed with a namespace-qualified key ({@link XsdDomUtil#qualifiedKey})
     * instead of silently overwriting each other — mirrors {@code XsdToSchema.addChoiceFields}.
     */
    private List<FieldBinding> bindFields(Element container, Element schemaRoot, String defaultNs) {
        var refs = new ArrayList<FieldRef>();
        for (Element el : xsdChildren(container)) {
            if (!"element".equals(el.getLocalName())) continue;

            String name = el.getAttribute("name");
            if (!name.isEmpty()) {
                // locally declared: use defaultNs (non-null only when elementFormDefault="qualified")
                refs.add(new FieldRef(name, defaultNs));
            } else {
                // ref= element: resolve the ref'd element's namespace
                String ref = el.getAttribute("ref");
                if (ref.isEmpty()) continue;
                String refNs = referencedNamespace(ref, el, schemaRoot);
                if (refNs != null && !refNs.isEmpty()) refs.add(new FieldRef(localName(ref), refNs));
            }
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

    /** The target namespace of the element a {@code ref="prefix:local"} points at. */
    private static String referencedNamespace(String ref, Element refEl, Element schemaRoot) {
        String refPrefix = prefix(ref);
        return refPrefix.isEmpty()
                ? schemaRoot.getAttribute("targetNamespace")
                : refEl.lookupNamespaceURI(refPrefix);
    }

    /**
     * The WSDL-derived metadata for the element currently being written: the order its fields are
     * declared in, the namespace each field carries, and the XSD declaration itself (needed to
     * derive the same three values for a nested object).
     */
    private record FieldContext(List<String> fieldOrder, Map<String, String> fieldNamespaces, XsdContext xsd) {}

    private static final FieldContext NO_FIELD_CONTEXT = new FieldContext(List.of(), Map.of(), null);

    /** The field metadata of {@code context}, or an empty context when the XSD declaration is unknown. */
    private FieldContext fieldContextFor(XsdContext context) {
        if (context == null) return NO_FIELD_CONTEXT;
        List<FieldBinding> bindings = extractFieldBindings(context);
        return new FieldContext(bindings.stream().map(FieldBinding::key).toList(),
                fieldNamespacesOf(bindings), context);
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
            parent.setTextContent(jsonNode.asText());
        }
    }

    private void emitField(String fieldName, JsonNode fieldValue, Element parent, Document doc, FieldContext context) {
        if (fieldName.startsWith(ATTRIBUTE_PREFIX)) {
            parent.setAttribute(fieldName.substring(ATTRIBUTE_PREFIX.length()), fieldValue.asText());
            return;
        }
        String ns = context.fieldNamespaces().get(fieldName);
        String xmlLocalName = localNameFromKey(fieldName);
        FieldContext childContext = fieldContextFor(childXsdContext(context.xsd(), xmlLocalName));

        if (fieldValue.isArray()) {
            for (JsonNode arrayItem : fieldValue) {
                Element arrayElement = makeElement(doc, ns, xmlLocalName);
                if (arrayItem.isValueNode()) {
                    arrayElement.setTextContent(arrayItem.asText());
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
                childElement.setTextContent(fieldValue.asText());
            }
        }
    }

    /** The XSD declaration of the named child field, or {@code null} if it cannot be resolved. */
    private XsdContext childXsdContext(XsdContext parentContext, String childLocalName) {
        return parentContext != null ? findChildXsdContext(parentContext, childLocalName) : null;
    }

    private static Element makeElement(Document doc, String namespace, String name) {
        return namespace != null ? doc.createElementNS(namespace, name) : doc.createElement(name);
    }

    private byte[] documentToBytes(Document doc) throws Exception {
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "no");
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        transformer.transform(new DOMSource(doc), new StreamResult(outputStream));
        return outputStream.toByteArray();
    }

}
