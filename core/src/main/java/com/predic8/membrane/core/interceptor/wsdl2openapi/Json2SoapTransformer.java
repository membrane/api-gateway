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

import com.predic8.membrane.core.util.xml.schema.XsdToSchema;
import com.predic8.membrane.core.util.xml.schema.Json2XmlTransformer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.core.util.wsdl.parser.Message;
import com.predic8.membrane.core.util.wsdl.parser.Operation;
import com.predic8.membrane.core.util.wsdl.parser.Operation.Direction;
import com.predic8.membrane.core.util.wsdl.parser.Part;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.namespace.QName;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS;
import static com.predic8.membrane.annot.Constants.SOAP11_NS;
import static com.predic8.membrane.annot.Constants.SOAP12_NS;
import static com.predic8.membrane.core.util.xml.schema.Json2XmlTransformer.documentToBytes;
import static com.predic8.membrane.core.util.wsdl.parser.Definitions.SOAPVersion.SOAP_12;

/**
 * Transforms a JSON body to a SOAP XML envelope: the input message of an operation, or, for a
 * transformer built for {@link Direction#OUTPUT}, its output message. The Body's element is written
 * by {@link Json2XmlTransformer}, which describes how JSON keys and values become XML.
 *
 * <p>One instance serves every concurrent exchange of its operation, so everything taken from the
 * WSDL is resolved once, in the constructor, and {@link #transform} only reads the immutable result
 * — see {@link Json2XmlTransformer}.
 */
public class Json2SoapTransformer {

    private static final ObjectMapper MAPPER = new ObjectMapper().enable(FAIL_ON_TRAILING_TOKENS);

    private final String operationName;
    private final Direction direction;
    private final boolean soap12;
    /** Writes the element of the operation's message, or {@code null} when the WSDL does not define a usable one. */
    private final Json2XmlTransformer bodyElement;
    /** Why the operation has no usable body element, reported by every {@link #transform} call. */
    private final String unusableReason;

    /**
     * @param schemasByNamespace the import graph resolved once by the caller — see
     *                           {@link XsdToSchema#getSchemasByNamespace()}. One transformer is built
     *                           per operation, so deriving it here would walk the graph once per
     *                           operation instead of once per API.
     * @param port               the port whose port type declares the operation, and whose binding
     *                           decides the SOAP version of the envelope.
     */
    public Json2SoapTransformer(SelectedPort port, String operationName,
                                Map<String, List<Element>> schemasByNamespace) {
        this(port, operationName, Direction.INPUT, schemasByNamespace);
    }

    /**
     * @param direction {@link Direction#INPUT} for a request, {@link Direction#OUTPUT} for a response
     * @see #Json2SoapTransformer(SelectedPort, String, Map)
     */
    Json2SoapTransformer(SelectedPort port, String operationName, Direction direction,
                         Map<String, List<Element>> schemasByNamespace) {
        this.operationName = operationName;
        this.direction = direction;
        this.soap12 = port.soapVersion() == SOAP_12;

        Json2XmlTransformer resolved = null;
        String reason = null;
        try {
            resolved = new Json2XmlTransformer(resolveBodyElement(port), schemasByNamespace);
        } catch (IllegalArgumentException e) {
            // Reported per request, as before: one unusable operation must not stop the others.
            reason = e.getMessage();
        }
        this.bodyElement = resolved;
        this.unusableReason = reason;
    }

    public byte[] transform(String jsonBody) throws Exception {
        JsonNode jsonNode = MAPPER.readTree(jsonBody);

        if (bodyElement == null) {
            throw new IllegalArgumentException(unusableReason);
        }

        Envelope envelope = createSoapEnvelope();
        bodyElement.appendTo(envelope.doc(), envelope.body(), jsonNode);
        return documentToBytes(envelope.doc());
    }

    /**
     * The element of the operation's message in this transformer's direction.
     *
     * @throws IllegalArgumentException if the operation, its message, or the element of its first
     *                                  part cannot be found
     */
    private QName resolveBodyElement(SelectedPort port) {
        Operation operation = port.findOperation(operationName)
                .orElseThrow(() -> new IllegalArgumentException("Operation not found: " + operationName));

        final var messageKind = direction.name().toLowerCase(Locale.ROOT);
        Message message = operation.getMessagesByDirection(direction).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No %s message found for operation: %s".formatted(messageKind, operationName)));
        List<Part> parts = message.getParts();
        if (parts.isEmpty()) {
            throw new IllegalArgumentException("The %s message has no parts for operation: %s".formatted(messageKind, operationName));
        }
        Part part = parts.getFirst();
        QName element = part.getElementQName();
        if (element == null) {
            throw new IllegalArgumentException("Part has no element name for operation: " + operationName);
        }
        return element;
    }

    /** A freshly created SOAP envelope document, together with its Body element. */
    private record Envelope(Document doc, Element body) {}

    private Envelope createSoapEnvelope() throws Exception {
        Document doc = Json2XmlTransformer.newDocument();

        String soapNs = soap12 ? SOAP12_NS : SOAP11_NS;
        String prefix = soap12 ? "s12" : "s11";

        Element envelope = doc.createElementNS(soapNs, prefix + ":Envelope");
        envelope.setAttribute("xmlns:" + prefix, soapNs);
        doc.appendChild(envelope);

        Element body = doc.createElementNS(soapNs, prefix + ":Body");
        envelope.appendChild(body);

        return new Envelope(doc, body);
    }
}
