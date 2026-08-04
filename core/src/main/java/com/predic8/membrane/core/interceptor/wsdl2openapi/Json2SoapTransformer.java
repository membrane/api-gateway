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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

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

import static com.predic8.membrane.annot.Constants.XSD_NS;
import static com.predic8.membrane.core.interceptor.wsdl2openapi.XsdDomUtil.*;
import static com.predic8.membrane.core.util.wsdl.parser.Definitions.SOAPVersion.SOAP_11;
import static com.predic8.membrane.core.util.wsdl.parser.Definitions.SOAPVersion.SOAP_12;
import static com.predic8.membrane.core.util.wsdl.parser.Operation.Direction.INPUT;

/**
 * Transforms JSON request to SOAP XML envelope.
 * JSON keys prefixed with "@" are mapped to XML attributes instead of child elements.
 */
public class Json2SoapTransformer {

    private static final Logger log = LoggerFactory.getLogger(Json2SoapTransformer.class);
    private static final String SOAP11_NS = "http://schemas.xmlsoap.org/soap/envelope/";
    private static final String SOAP12_NS = "http://www.w3.org/2003/05/soap-envelope";

    private final Definitions definitions;
    private final String operationName;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, List<Element>> schemasByNamespace;

    public Json2SoapTransformer(Definitions definitions, String operationName) {
        this.definitions = definitions;
        this.operationName = operationName;
        this.schemasByNamespace = buildSchemaMap(definitions);
    }

    public byte[] transform(String jsonBody) throws Exception {
        JsonNode jsonNode = mapper.readTree(jsonBody);

        List<Message> inputMessages = findOperation(operationName).getMessagesByDirection(INPUT);
        if (inputMessages.isEmpty()) {
            throw new IllegalArgumentException("No input message found for operation: " + operationName);
        }
        Message inputMessage = inputMessages.getFirst();

        Document doc = createSoapEnvelope();
        Element body = getSoapBody(doc);

        Element operationElement = createOperationElement(doc, inputMessage);
        body.appendChild(operationElement);

        List<String> fieldOrder = resolveElementFieldOrder(inputMessage.getParts().getFirst().getElementQName());
        Map<String, String> fieldNamespaces = resolveFieldNamespaces(inputMessage.getParts().getFirst().getElementQName());
        mapJsonToElement(jsonNode, operationElement, doc, fieldOrder, fieldNamespaces);

        return documentToBytes(doc);
    }

    private Operation findOperation(String name) {
        return definitions.getPortTypes().stream()
                .flatMap(pt -> pt.getOperations().stream())
                .filter(op -> name.equals(op.getName()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Operation not found: " + name));
    }

    private Document createSoapEnvelope() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.newDocument();

        String soapNs = useSoap12() ? SOAP12_NS : SOAP11_NS;

        Element envelope = doc.createElementNS(soapNs, "soap:Envelope");
        envelope.setAttribute("xmlns:soap", soapNs);
        doc.appendChild(envelope);

        Element body = doc.createElementNS(soapNs, "soap:Body");
        envelope.appendChild(body);

        return doc;
    }

    private boolean useSoap12() {
        Set<Definitions.SOAPVersion> versions = definitions.getSoapVersions();
        return versions.contains(SOAP_12) && !versions.contains(SOAP_11);
    }

    private Element getSoapBody(Document doc) {
        NodeList bodies = doc.getElementsByTagNameNS("*", "Body");
        return (Element) bodies.item(0);
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
     * Returns the field names in WSDL-defined order for the element identified by {@code elementQName}.
     * Returns an empty list if the element cannot be found or has no resolvable sequence.
     */
    private List<String> resolveElementFieldOrder(QName elementQName) {
        if (elementQName == null) return List.of();
        var roots = schemasByNamespace.get(elementQName.getNamespaceURI());
        if (roots == null) return List.of();
        for (var schemaRoot : roots) {
            Element xsdEl = findXsdChildWithName(schemaRoot, "element", elementQName.getLocalPart());
            if (xsdEl != null) {
                return extractSequenceFieldNames(xsdEl, schemaRoot);
            }
        }
        return List.of();
    }

    /**
     * Extracts ordered field names from the complexType of an xsd:element node.
     * Handles inline complexType and type-attribute references to named complexTypes.
     */
    private List<String> extractSequenceFieldNames(Element xsdElement, Element schemaRoot) {
        Element complexType = findXsdChild(xsdElement, "complexType");
        if (complexType == null) {
            String typeAttr = xsdElement.getAttribute("type");
            if (!typeAttr.isEmpty()) {
                complexType = resolveComplexType(typeAttr, xsdElement, schemaRoot);
            }
        }
        if (complexType == null) return List.of();

        Element sequence = findXsdChild(complexType, "sequence");
        if (sequence == null) sequence = findXsdChild(complexType, "all");
        if (sequence == null) return List.of();

        var names = new ArrayList<String>();
        NodeList children = sequence.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element el && XSD_NS.equals(el.getNamespaceURI()) && "element".equals(el.getLocalName())) {
                String name = el.getAttribute("name");
                if (!name.isEmpty()) names.add(name);
            }
        }
        return names;
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
     * Builds a map from field name to namespace URI for the children of the XSD element
     * identified by {@code elementQName}. Locally-declared fields carry the schema's
     * {@code targetNamespace} when {@code elementFormDefault="qualified"}; {@code ref=}
     * fields carry their referenced element's target namespace regardless of
     * {@code elementFormDefault}.
     */
    private Map<String, String> resolveFieldNamespaces(QName elementQName) {
        if (elementQName == null) return Map.of();
        List<Element> roots = schemasByNamespace.get(elementQName.getNamespaceURI());
        if (roots == null) return Map.of();
        for (var schemaRoot : roots) {
            Element xsdEl = findXsdChildWithName(schemaRoot, "element", elementQName.getLocalPart());
            if (xsdEl != null) {
                String defaultNs = "qualified".equals(schemaRoot.getAttribute("elementFormDefault"))
                        ? schemaRoot.getAttribute("targetNamespace") : null;
                return extractFieldNamespaces(xsdEl, schemaRoot, defaultNs);
            }
        }
        return Map.of();
    }

    private Map<String, String> extractFieldNamespaces(Element xsdElement, Element schemaRoot, String defaultNs) {
        Element complexType = findXsdChild(xsdElement, "complexType");
        if (complexType == null) {
            String typeAttr = xsdElement.getAttribute("type");
            if (!typeAttr.isEmpty()) complexType = resolveComplexType(typeAttr, xsdElement, schemaRoot);
        }
        if (complexType == null) return Map.of();

        var result = new LinkedHashMap<String, String>();
        for (String tag : List.of("sequence", "all", "choice")) {
            Element container = findXsdChild(complexType, tag);
            if (container != null) {
                collectFieldNamespaces(container, schemaRoot, defaultNs, result);
                break;
            }
        }
        return result;
    }

    /** A field name/namespace pair, resolved prior to deciding its final (possibly-qualified) key. */
    private record FieldRef(String localName, String namespaceURI) {}

    /**
     * Resolves every field in {@code container} to a {@link FieldRef} first, so that fields
     * whose local name collides across namespaces (e.g. two {@code ref}'d elements both named
     * {@code value}, from different namespaces) can be keyed with a namespace-qualified key
     * ({@link XsdDomUtil#qualifiedKey}) instead of silently overwriting each other in
     * {@code result} — mirrors {@code XsdToSchema.addChoiceFields}.
     */
    private void collectFieldNamespaces(Element container, Element schemaRoot, String defaultNs, Map<String, String> result) {
        var refs = new ArrayList<FieldRef>();
        NodeList children = container.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (!(child instanceof Element el) || !XSD_NS.equals(el.getNamespaceURI())) continue;
            if (!"element".equals(el.getLocalName())) continue;

            String name = el.getAttribute("name");
            if (!name.isEmpty()) {
                // locally declared: use defaultNs (non-null only when elementFormDefault="qualified")
                refs.add(new FieldRef(name, defaultNs));
            } else {
                // ref= element: resolve the ref'd element's namespace
                String ref = el.getAttribute("ref");
                if (ref.isEmpty()) continue;
                String refPrefix = prefix(ref);
                String refNs = refPrefix.isEmpty()
                        ? schemaRoot.getAttribute("targetNamespace")
                        : el.lookupNamespaceURI(refPrefix);
                if (refNs != null && !refNs.isEmpty()) refs.add(new FieldRef(localName(ref), refNs));
            }
        }

        var occurrences = new HashMap<String, Integer>();
        for (var r : refs) {
            occurrences.merge(r.localName(), 1, Integer::sum);
        }
        for (var r : refs) {
            if (r.namespaceURI() == null) continue;
            boolean collides = occurrences.get(r.localName()) > 1;
            String key = collides ? qualifiedKey(r.namespaceURI(), r.localName()) : r.localName();
            result.put(key, r.namespaceURI());
        }
    }

    private void mapJsonToElement(JsonNode jsonNode, Element parent, Document doc, List<String> fieldOrder, Map<String, String> fieldNamespaces) {
        if (jsonNode.isObject()) {
            var emitted = new LinkedHashSet<String>();
            for (String name : fieldOrder) {
                JsonNode val = jsonNode.get(name);
                if (val != null) {
                    emitField(name, val, parent, doc, fieldNamespaces);
                    emitted.add(name);
                }
            }
            for (var entry : jsonNode.properties()) {
                if (!emitted.contains(entry.getKey())) {
                    emitField(entry.getKey(), entry.getValue(), parent, doc, fieldNamespaces);
                }
            }
        } else if (jsonNode.isValueNode()) {
            parent.setTextContent(jsonNode.asText());
        }
    }

    private void emitField(String fieldName, JsonNode fieldValue, Element parent, Document doc, Map<String, String> fieldNamespaces) {
        if (fieldName.startsWith("@")) {
            parent.setAttribute(fieldName.substring(1), fieldValue.asText());
            return;
        }
        String ns = fieldNamespaces.get(fieldName);
        String xmlLocalName = localNameFromKey(fieldName);
        if (fieldValue.isArray()) {
            for (JsonNode arrayItem : fieldValue) {
                Element arrayElement = makeElement(doc, ns, xmlLocalName);
                if (arrayItem.isValueNode()) {
                    arrayElement.setTextContent(arrayItem.asText());
                } else {
                    mapJsonToElement(arrayItem, arrayElement, doc, List.of(), Map.of());
                }
                parent.appendChild(arrayElement);
            }
        } else {
            Element childElement = makeElement(doc, ns, xmlLocalName);
            parent.appendChild(childElement);
            if (fieldValue.isObject()) {
                mapJsonToElement(fieldValue, childElement, doc, List.of(), Map.of());
            } else {
                childElement.setTextContent(fieldValue.asText());
            }
        }
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
