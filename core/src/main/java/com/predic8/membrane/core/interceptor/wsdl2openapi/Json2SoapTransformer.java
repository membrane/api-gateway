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

import com.fasterxml.jackson.databind.*;
import com.predic8.membrane.core.util.wsdl.parser.*;
import org.slf4j.*;
import org.w3c.dom.*;

import javax.xml.namespace.*;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.*;
import javax.xml.transform.stream.*;
import java.io.*;
import java.util.*;

import static com.predic8.membrane.annot.Constants.*;
import static com.predic8.membrane.core.util.wsdl.parser.Definitions.SOAPVersion.*;
import static com.predic8.membrane.core.util.wsdl.parser.Operation.Direction.INPUT;

/**
 * Transforms JSON request to SOAP XML envelope
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
        mapJsonToElement(jsonNode, operationElement, doc, fieldOrder);

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
        int colon = typeRef.indexOf(':');
        String prefix = colon >= 0 ? typeRef.substring(0, colon) : "";
        String local = colon >= 0 ? typeRef.substring(colon + 1) : typeRef;

        List<Element> targetRoots;
        if (prefix.isEmpty()) {
            targetRoots = List.of(currentSchemaRoot);
        } else {
            String nsUri = contextElement.lookupNamespaceURI(prefix);
            if (nsUri == null) {
                targetRoots = List.of(currentSchemaRoot);
            } else {
                var roots = schemasByNamespace.get(nsUri);
                targetRoots = (roots != null && !roots.isEmpty()) ? roots : List.of(currentSchemaRoot);
            }
        }

        for (var root : targetRoots) {
            Element complexType = findXsdChildWithName(root, "complexType", local);
            if (complexType != null) return complexType;
        }
        return null;
    }

    private void mapJsonToElement(JsonNode jsonNode, Element parent, Document doc, List<String> fieldOrder) {
        if (jsonNode.isObject()) {
            var emitted = new LinkedHashSet<String>();
            for (String name : fieldOrder) {
                JsonNode val = jsonNode.get(name);
                if (val != null) {
                    emitField(name, val, parent, doc);
                    emitted.add(name);
                }
            }
            for (var entry : jsonNode.properties()) {
                if (!emitted.contains(entry.getKey())) {
                    emitField(entry.getKey(), entry.getValue(), parent, doc);
                }
            }
        } else if (jsonNode.isValueNode()) {
            parent.setTextContent(jsonNode.asText());
        }
    }

    private void emitField(String fieldName, JsonNode fieldValue, Element parent, Document doc) {
        if (fieldValue.isArray()) {
            for (JsonNode arrayItem : fieldValue) {
                Element arrayElement = doc.createElement(fieldName);
                if (arrayItem.isValueNode()) {
                    arrayElement.setTextContent(arrayItem.asText());
                } else {
                    mapJsonToElement(arrayItem, arrayElement, doc, List.of());
                }
                parent.appendChild(arrayElement);
            }
        } else {
            Element childElement = doc.createElement(fieldName);
            parent.appendChild(childElement);
            if (fieldValue.isObject()) {
                mapJsonToElement(fieldValue, childElement, doc, List.of());
            } else {
                childElement.setTextContent(fieldValue.asText());
            }
        }
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

    private static Map<String, List<Element>> buildSchemaMap(Definitions definitions) {
        var map = new LinkedHashMap<String, List<Element>>();
        var queue = new ArrayDeque<>(definitions.getSchemas());
        var seen = Collections.newSetFromMap(new IdentityHashMap<>());
        seen.addAll(definitions.getSchemas());
        while (!queue.isEmpty()) {
            var schema = queue.poll();
            var ns = schema.getTargetNamespace();
            if (ns != null) {
                map.computeIfAbsent(ns, k -> new ArrayList<>()).add(schema.getSchemaElement());
            }
            for (var imp : schema.getImports()) {
                var imported = imp.getSchema();
                if (imported != null && seen.add(imported)) {
                    queue.add(imported);
                }
            }
        }
        return map;
    }

    private static Element findXsdChild(Element parent, String xsdLocalName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element el && XSD_NS.equals(el.getNamespaceURI()) && xsdLocalName.equals(el.getLocalName())) {
                return el;
            }
        }
        return null;
    }

    private static Element findXsdChildWithName(Element parent, String xsdLocalName, String nameAttr) {
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
}
