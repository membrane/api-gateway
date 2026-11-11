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
package com.predic8.membrane.core.interceptor.soap2json;

import com.predic8.membrane.core.util.xml.schema.UndeclaredElementException;
import com.predic8.membrane.core.util.xml.schema.XsdToSchema;
import com.predic8.membrane.core.util.ConfigurationException;
import com.predic8.membrane.core.util.wsdl.parser.Message;
import com.predic8.membrane.core.util.wsdl.parser.Operation;
import io.swagger.v3.oas.models.media.Schema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.namespace.QName;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
 * @param requestSchemas    the schema of each element an input message carries, by its qualified name
 * @param responseSchemas   the schema of each element an output message carries, by its qualified
 *                          name. Kept apart from the inputs, so that a request cannot carry a
 *                          response and a response cannot carry a request.
 * @param inputOperations   the operation whose input message carries the element, by the
 *                          element's qualified name. An element that the input of several
 *                          operations carries is missing: its operation cannot be told from the
 *                          Body, and WS-I Basic Profile R2710 rules such WSDLs out anyway.
 * @param faultDetailSchema types the detail of a SOAP Fault: one property per fault the port's
 *                          operations declare, since a Fault does not say which operation failed
 * @param components        the named types the schemas refer to; see {@link XsdToSchema#getComponents()}
 */
record SoapBodySchemas(String portName,
                       Map<QName, Schema<?>> requestSchemas,
                       Map<QName, Schema<?>> responseSchemas,
                       Map<QName, String> inputOperations,
                       Schema<?> faultDetailSchema,
                       Map<String, Schema<?>> components) {

    private static final Logger log = LoggerFactory.getLogger(SoapBodySchemas.class);

    /**
     * Converts every message of the port's operations.
     *
     * @throws ConfigurationException if the port declares no message this class can index
     */
    static SoapBodySchemas of(SelectedPort port, WsdlPartsToSchema xsdToSchema) {
        final var converted = new LinkedHashMap<QName, Schema<?>>();
        final var requestSchemas = new LinkedHashMap<QName, Schema<?>>();
        final var responseSchemas = new LinkedHashMap<QName, Schema<?>>();
        final var inputOperations = new LinkedHashMap<QName, String>();
        final var sharedInputs = new HashSet<QName>();
        final var faults = new ArrayList<Operation.Fault>();
        for (final Operation operation : port.operations()) {
            for (final Message message : operation.getMessagesByDirection(INPUT)) {
                addMessage(message, operation, xsdToSchema, converted, requestSchemas)
                        .ifPresent(element -> addInputOperation(element, operation, inputOperations, sharedInputs));
            }
            for (final Message message : operation.getMessagesByDirection(OUTPUT)) {
                addMessage(message, operation, xsdToSchema, converted, responseSchemas);
            }
            // A fault two operations declare is converted once — see WsdlPartsToSchema.faultDetailKeys.
            faults.addAll(operation.getFaults());
        }
        if (converted.isEmpty()) {
            throw new ConfigurationException("""
                    Port '%s' declares no document/literal message with a single element part.
                    Only such messages can be converted: RPC-style and multi-part messages are not supported.""".formatted(port.name()));
        }
        final var faultDetailSchema = xsdToSchema.convertFaultDetail(List.copyOf(faults));
        // Copied only now: converting the fault details may still have added components.
        return new SoapBodySchemas(port.name(), Map.copyOf(requestSchemas), Map.copyOf(responseSchemas), Map.copyOf(inputOperations),
                faultDetailSchema, Map.copyOf(xsdToSchema.getComponents()));
    }

    /**
     * Indexes the message into {@code schemas}; returns its element, or empty if the message cannot
     * be indexed.
     *
     * @param converted the schema of every element converted so far, in either direction
     */
    private static Optional<QName> addMessage(Message message, Operation operation, WsdlPartsToSchema xsdToSchema,
                                              Map<QName, Schema<?>> converted, Map<QName, Schema<?>> schemas) {
        final var parts = message.getParts();
        if (parts.size() != 1 || parts.getFirst().getElementQName() == null) {
            log.debug("Message '{}' of operation '{}' is not a document/literal message with a single element part, skipping",
                    message.getName(), operation.getName());
            return Optional.empty();
        }
        final var element = parts.getFirst().getElementQName();
        // The same element always yields the same schema, so it is converted once.
        if (!converted.containsKey(element)) {
            converted.put(element, xsdToSchema.convert(element));
        }
        schemas.put(element, converted.get(element));
        return Optional.of(element);
    }

    private static void addInputOperation(QName element, Operation operation, Map<QName, String> inputOperations,
                                          Set<QName> sharedInputs) {
        if (sharedInputs.contains(element)) return;
        final var first = inputOperations.putIfAbsent(element, operation.getName());
        if (first == null) return;
        log.debug("The input element {} is shared by operations '{}' and '{}', so its operation cannot be told from the Body",
                element, first, operation.getName());
        inputOperations.remove(element);
        sharedInputs.add(element);
    }

    /**
     * The schema of the input message whose Body carries {@code element}.
     *
     * @throws UndeclaredElementException if no input message of the port carries that element
     */
    Schema<?> forRequest(QName element) throws UndeclaredElementException {
        return lookup(requestSchemas, element, "input");
    }

    /**
     * The schema of the output message whose Body carries {@code element}.
     *
     * @throws UndeclaredElementException if no output message of the port carries that element
     */
    Schema<?> forResponse(QName element) throws UndeclaredElementException {
        return lookup(responseSchemas, element, "output");
    }

    private Schema<?> lookup(Map<QName, Schema<?>> schemas, QName element, String direction) throws UndeclaredElementException {
        final var schema = schemas.get(element);
        if (schema == null) {
            throw new UndeclaredElementException(element, "any %s message of port '%s'".formatted(direction, portName));
        }
        return schema;
    }

    /** The operation whose input message carries {@code element}; empty if there is not exactly one. */
    Optional<String> inputOperation(QName element) {
        return Optional.ofNullable(inputOperations.get(element));
    }
}
