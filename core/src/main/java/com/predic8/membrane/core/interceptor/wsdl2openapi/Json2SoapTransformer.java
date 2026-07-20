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

import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.*;
import javax.xml.transform.stream.*;
import java.io.*;
import java.util.*;

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

    public Json2SoapTransformer(Definitions definitions, String operationName) {
        this.definitions = definitions;
        this.operationName = operationName;
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

        mapJsonToElement(jsonNode, operationElement, doc);

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

    private void mapJsonToElement(JsonNode jsonNode, Element parent, Document doc) {
        if (jsonNode.isObject()) {
            for (Map.Entry<String, JsonNode> field : jsonNode.properties()) {
                String fieldName = field.getKey();
                JsonNode fieldValue = field.getValue();

                if (fieldValue.isArray()) {
                    for (JsonNode arrayItem : fieldValue) {
                        Element arrayElement = doc.createElement(fieldName);
                        if (arrayItem.isValueNode()) {
                            arrayElement.setTextContent(arrayItem.asText());
                        } else {
                            mapJsonToElement(arrayItem, arrayElement, doc);
                        }
                        parent.appendChild(arrayElement);
                    }
                } else {
                    Element childElement = doc.createElement(fieldName);
                    parent.appendChild(childElement);
                    if (fieldValue.isObject()) {
                        mapJsonToElement(fieldValue, childElement, doc);
                    } else {
                        childElement.setTextContent(fieldValue.asText());
                    }
                }
            }
        } else if (jsonNode.isValueNode()) {
            parent.setTextContent(jsonNode.asText());
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
}
