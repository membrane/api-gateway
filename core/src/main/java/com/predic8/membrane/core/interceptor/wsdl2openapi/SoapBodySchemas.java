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

import com.predic8.membrane.core.util.ConfigurationException;
import com.predic8.membrane.core.util.wsdl.parser.Message;
import com.predic8.membrane.core.util.wsdl.parser.Operation;
import io.swagger.v3.oas.models.media.Schema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.namespace.QName;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.predic8.membrane.core.util.wsdl.parser.Operation.Direction.INPUT;
import static com.predic8.membrane.core.util.wsdl.parser.Operation.Direction.OUTPUT;

/**
 * The schemas of every message a WSDL port exchanges, keyed by the element its SOAP Body carries.
 * Converting a SOAP message without knowing its operation starts here: the Body's element names
 * the message, and with it the schema to type the content with.
 *
 * <p>Built completely in {@code init()} and immutable afterwards. {@link XsdToSchema} fills its
 * components while it converts, and the WSDL model is a Xerces DOM that concurrent reads corrupt,
 * so neither may be touched once requests are served.
 *
 * <p>Only document/literal messages with a single element part are indexed: the Body then carries
 * exactly that element. An RPC-style part names a type, not an element, and a message of several
 * parts puts several elements into the Body, of which {@link Soap2JsonTransformer} reads only the
 * first.
 *
 * @param portName          the name of the port, for error messages
 * @param bodySchemas       the schema of each Body element, by its qualified name
 * @param faultDetailSchema types the detail of a SOAP Fault: one property per fault the port's
 *                          operations declare, since a Fault does not say which operation failed
 * @param components        the named types the schemas refer to; see {@link XsdToSchema#getComponents()}
 */
record SoapBodySchemas(String portName,
                       Map<QName, Schema<?>> bodySchemas,
                       Schema<?> faultDetailSchema,
                       Map<String, Schema<?>> components) {

    private static final Logger log = LoggerFactory.getLogger(SoapBodySchemas.class);

    /**
     * Converts every message of the port's operations.
     *
     * @throws ConfigurationException if the port declares no message this class can index
     */
    static SoapBodySchemas of(SelectedPort port, XsdToSchema xsdToSchema) {
        final var bodySchemas = new LinkedHashMap<QName, Schema<?>>();
        final var faults = new LinkedHashMap<String, Operation.Fault>();
        for (final Operation operation : port.operations()) {
            for (final Message message : operation.getMessagesByDirection(INPUT)) {
                addMessage(message, operation, xsdToSchema, bodySchemas);
            }
            for (final Message message : operation.getMessagesByDirection(OUTPUT)) {
                addMessage(message, operation, xsdToSchema, bodySchemas);
            }
            for (final Operation.Fault fault : operation.getFaults()) {
                final var parts = fault.getMessage().getParts();
                if (!parts.isEmpty()) faults.putIfAbsent(XsdToSchema.faultDetailKey(parts), fault);
            }
        }
        if (bodySchemas.isEmpty()) {
            throw new ConfigurationException("""
                    Port '%s' declares no document/literal message with a single element part.
                    Only such messages can be converted: RPC-style and multi-part messages are not supported.""".formatted(port.name()));
        }
        final var faultDetailSchema = xsdToSchema.convertFaultDetail(List.copyOf(faults.values()));
        // Copied only now: converting the fault details may still have added components.
        return new SoapBodySchemas(port.name(), Map.copyOf(bodySchemas), faultDetailSchema,
                Map.copyOf(xsdToSchema.getComponents()));
    }

    private static void addMessage(Message message, Operation operation, XsdToSchema xsdToSchema,
                                   Map<QName, Schema<?>> bodySchemas) {
        final var parts = message.getParts();
        if (parts.size() != 1 || parts.getFirst().getElementQName() == null) {
            log.debug("Message '{}' of operation '{}' is not a document/literal message with a single element part, skipping",
                    message.getName(), operation.getName());
            return;
        }
        final var element = parts.getFirst().getElementQName();
        // The same element always yields the same schema, so the first message to name it wins.
        if (!bodySchemas.containsKey(element)) {
            bodySchemas.put(element, xsdToSchema.convert(element));
        }
    }

    /**
     * The schema of the message whose Body carries {@code element}.
     *
     * @throws UndeclaredBodyElementException if no message of the port carries that element
     */
    Schema<?> forElement(QName element) throws UndeclaredBodyElementException {
        final var schema = bodySchemas.get(element);
        if (schema == null) throw new UndeclaredBodyElementException(element, portName);
        return schema;
    }
}
