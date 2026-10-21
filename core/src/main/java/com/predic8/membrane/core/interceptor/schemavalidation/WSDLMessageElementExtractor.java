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

package com.predic8.membrane.core.interceptor.schemavalidation;

import com.predic8.membrane.core.util.ConfigurationException;
import com.predic8.membrane.core.util.wsdl.parser.*;
import com.predic8.membrane.core.util.wsdl.parser.Operation.Direction;
import com.predic8.membrane.core.util.wsdl.parser.schema.Schema;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import javax.xml.namespace.QName;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

import static com.predic8.membrane.core.util.wsdl.parser.Binding.Style.RPC;
import static com.predic8.membrane.core.util.wsdl.parser.Operation.Direction.*;
import static java.util.stream.Collectors.toSet;

public class WSDLMessageElementExtractor {

    private static final Logger log = LoggerFactory.getLogger(WSDLMessageElementExtractor.class.getName());

    public static Set<QName> getPossibleRequestElements(Definitions definitions, String serviceName) {
        return getPossibleElements(definitions, INPUT, serviceName);
    }

    public static Set<QName> getPossibleResponseElements(Definitions definitions, String serviceName) {
        return getPossibleElements(definitions, OUTPUT, serviceName);
    }

    /**
     * Header blocks a request may carry, as declared via {@code soap:header} on the inputs of the
     * service's binding operations.
     */
    public static Set<QName> getPossibleRequestHeaderElements(Definitions definitions, String serviceName) {
        return getHeaderElements(getHeaders(definitions, serviceName, BindingOperation::getInputs), getGlobalElements(definitions));
    }

    /**
     * Header blocks a response may carry, as declared via {@code soap:header} on the outputs of the
     * service's binding operations.
     */
    public static Set<QName> getPossibleResponseHeaderElements(Definitions definitions, String serviceName) {
        return getHeaderElements(getHeaders(definitions, serviceName, BindingOperation::getOutputs), getGlobalElements(definitions));
    }

    /**
     * The global element declarations of the WSDL's schemas, including those of the schemas they
     * include or import.
     */
    public static Set<QName> getGlobalElements(Definitions definitions) {
        final var result = new HashSet<QName>();
        final var visited = Collections.newSetFromMap(new IdentityHashMap<Schema, Boolean>());
        definitions.getSchemas().forEach(schema -> addGlobalElements(schema, result, visited));
        return result;
    }

    /**
     * Included elements are already merged into the including schema and take its target
     * namespace. Embedded schemas are linked to each other by namespace, so imports may form a
     * cycle; each schema is visited only once.
     */
    private static void addGlobalElements(Schema schema, Set<QName> result, Set<Schema> visited) {
        if (!visited.add(schema))
            return;
        for (var element : schema.getSchemaElements()) {
            result.add(new QName(schema.getTargetNamespace(), element.getName()));
        }
        for (var schemaImport : schema.getImports()) {
            if (schemaImport.getSchema() != null)
                addGlobalElements(schemaImport.getSchema(), result, visited);
        }
    }

    /**
     * A {@code soap:header} whose header block cannot be validated is skipped with a warning rather
     * than rejected: the rest of the WSDL is still validatable, and the header block is just not
     * validated. That is the case if its part cannot be found, if the part names a type instead of
     * an element, or if none of the WSDL's schemas declares the element, e.g. WS-Security bound
     * without its schema.
     *
     * @param declared the global elements of the WSDL's schemas
     */
    private static Set<QName> getHeaderElements(List<SoapHeader> headers, Set<QName> declared) {
        var result = new HashSet<QName>();
        for (var header : headers) {
            var resolved = header.resolvePart();
            if (resolved.isEmpty()) {
                log.warn("soap:header part {} of message {} cannot be found, so the header block it describes is not validated.", header.getPart(), header.getMessage());
                continue;
            }
            var part = resolved.get();
            var element = part.getElementQName();
            if (element == null) {
                log.warn("soap:header part {} names a type instead of an element, so the header block it describes is not validated.", part.getName());
                continue;
            }
            if (!declared.contains(element)) {
                log.warn("soap:header part {} of message {} binds element {}, which none of the WSDL's schemas declares, so the header block it describes is not validated.", header.getPart(), header.getMessage(), element);
                continue;
            }
            result.add(element);
        }
        return result;
    }

    private static List<SoapHeader> getHeaders(Definitions definitions, String serviceName, Function<BindingOperation, List<BindingOperationMessage>> messages) {
        return getHeaderBindings(definitions, serviceName).stream()
                .flatMap(binding -> binding.getBindingOperations().stream())
                .flatMap(operation -> messages.apply(operation).stream())
                .flatMap(message -> message.getHeaders().stream())
                .toList();
    }

    /**
     * Without services all bindings count, just as {@link #getTypesByStyle} then counts all port
     * types.
     */
    private static List<Binding> getHeaderBindings(Definitions definitions, String serviceName) {
        if (definitions.getServices().isEmpty()) {
            return definitions.getBindings();
        }
        return getBindings(definitions, serviceName);
    }

    /**
     * Message parts bound as {@code soap:header} on the operation's input resp. output in the
     * port type's bindings. They are carried in the SOAP header, so they are not the body element
     * of that message. A part bound as header anywhere else, e.g. on another operation or in the
     * other direction, may still be a body part here. Unresolvable ones are skipped silently;
     * {@link #getHeaderElements} already warns about them.
     */
    private static Set<Element> getHeaderPartElements(List<Binding> bindings, Operation operation, Direction direction) {
        if (direction == FAULT)
            return Set.of();
        return bindings.stream()
                .flatMap(binding -> binding.findBindingOperation(operation.getName()).stream())
                .flatMap(bindingOperation -> (direction == INPUT ? bindingOperation.getInputs() : bindingOperation.getOutputs()).stream())
                .flatMap(message -> message.getHeaders().stream())
                .map(SoapHeader::resolvePart)
                .flatMap(Optional::stream)
                .map(Part::getElement)
                .collect(toSet());
    }

    /**
     * The port type's bindings among those {@link #getHeaderBindings} considers.
     */
    private static List<Binding> getHeaderBindings(Definitions definitions, String serviceName, PortType portType) {
        final var names = getHeaderBindings(definitions, serviceName).stream().map(Binding::getName).collect(toSet());
        return definitions.getBindings(portType).stream()
                .filter(binding -> names.contains(binding.getName()))
                .toList();
    }

    /**
     * Elements that may legitimately appear as the payload of a SOAP fault's
     * {@code detail}/{@code Detail}, as declared via {@code wsdl:fault} on the service's
     * operations.
     * <p>
     * Unlike request and response elements, fault elements are read from the fault message's part
     * for RPC and document bindings alike: a {@code soap:fault} is never RPC-wrapped (WSDL 1.1
     * &sect;3.6), so there is no operation-named wrapper element to derive.
     *
     * @throws ConfigurationException if the WSDL declares a fault whose detail element cannot be
     *         determined - a fault message without a part, or with a part naming a type instead of
     *         an element. Such a fault is not validatable, and dropping it silently would leave an
     *         empty result indistinguishable from a WSDL that declares no faults at all, so fault
     *         validation would look active while doing nothing.
     */
    public static Set<QName> getPossibleFaultDetailElements(Definitions definitions, String serviceName) {
        var result = new HashSet<QName>();
        for (var portType : getAllPortTypes(getTypesByStyle(definitions, serviceName))) {
            for (var operation : portType.getOperations()) {
                for (var message : operation.getMessagesByDirection(FAULT)) {
                    result.add(getFaultDetailElement(operation, message));
                }
            }
        }
        return result;
    }

    /**
     * The element a {@code wsdl:fault}'s message declares as its detail payload.
     */
    private static QName getFaultDetailElement(Operation operation, Message message) {
        var parts = message.getParts();
        if (parts.isEmpty()) {
            throw new ConfigurationException("Fault message %s of operation %s has no part, so the fault's detail content cannot be validated. A wsdl:fault must reference a message with a single part naming an element."
                    .formatted(message.getName(), operation.getName()));
        }
        // A wsdl:fault message has exactly one part (WSDL 1.1 3.6), and only one detail entry is
        // validated at runtime. Extra parts are silently unused, so warn once here at startup
        // rather than letting the WSDL look fully covered while most of it is ignored. Not a
        // ConfigurationException: the first part still describes a validatable fault detail, so
        // refusing to start would be harsher than the defect warrants.
        if (parts.size() > 1) {
            log.warn("Fault message {} of operation {} declares {} parts, but a wsdl:fault must have a single part. Only the first one ({}) is used to validate the fault detail; the others are ignored.",
                    message.getName(), operation.getName(), parts.size(), parts.getFirst().getName());
        }
        var part = parts.getFirst();
        var element = part.getElementQName();
        if (element == null) {
            throw new ConfigurationException("Part %s of fault message %s (operation %s) names a type instead of an element, so the fault's detail content cannot be validated. A wsdl:fault's part must use part/@element, not part/@type."
                    .formatted(part.getName(), message.getName(), operation.getName()));
        }
        return element;
    }

    private static @NotNull List<PortType> getAllPortTypes(PortTypesByStyle portTypes) {
        return Stream.concat(portTypes.portTypesDocument().stream(), portTypes.portTypesRPC().stream()).toList();
    }

    public static Set<QName> getPossibleElements(Definitions definitions, Direction direction, String serviceName) {
        var portTypes = getTypesByStyle(definitions, serviceName);
        var result = new HashSet<>(getElementQNameForDocumentStyle(definitions, serviceName, direction, portTypes));
        result.addAll(getElementQNamesForRPCStyle(definitions, direction, portTypes));
        return result;
    }

    private static @NotNull Set<QName> getElementQNamesForRPCStyle(Definitions definitions, Direction direction, PortTypesByStyle portTypes) {
        return portTypes.portTypesRPC().stream().map(PortType::getOperations)
                .flatMap(Collection::stream)
                .filter(op -> !op.getMessagesByDirection(direction).isEmpty())
                .map(op -> new QName(definitions.getTargetNamespace(), getElementNameRPC(op, direction)))
                .collect(toSet());
    }

    private static @NotNull Set<QName> getElementQNameForDocumentStyle(Definitions definitions, String serviceName, Direction direction, PortTypesByStyle portTypes) {
        return getParts(definitions, serviceName, direction, portTypes.portTypesDocument())
                .map(Part::getElementQName)
                .filter(Objects::nonNull)
                .collect(toSet());
    }

    private static @NotNull PortTypesByStyle getTypesByStyle(Definitions definitions, String serviceName) {
        if (definitions.getServices().isEmpty()) {
            return new PortTypesByStyle(Collections.emptyList(), definitions.getPortTypes());
        }
        return getPortTypesByStyle(definitions, serviceName);
    }

    private static @NotNull PortTypesByStyle getPortTypesByStyle(Definitions definitions, String serviceName) {
        var portTypesRPC = new ArrayList<PortType>();
        var portTypesDocument = new ArrayList<PortType>();

        for (var binding : getBindings(definitions, serviceName)) {
            if (binding.getStyle() == RPC) {
                portTypesRPC.add(binding.getPortType());
                continue;
            }
            portTypesDocument.add(binding.getPortType());
        }
        return new PortTypesByStyle(portTypesRPC, portTypesDocument);
    }

    private static @NotNull List<Binding> getBindings(Definitions definitions, String serviceName) {
        return getServices(definitions, serviceName).stream()
                .flatMap(s -> s.getPorts().stream())
                .map(Port::getBinding).toList();
    }

    private static @NotNull List<Service> getServices(Definitions definitions, String serviceName) {
        if (serviceName != null) {
            var service = definitions.getService(serviceName);
            if (service.isEmpty()) {
                throw new IllegalArgumentException("WSDL does not contain service: " + serviceName);
            }
            return List.of(service.get());
        }
        return definitions.getServices();
    }

    private static String getElementNameRPC(Operation operation, Direction direction) {
        if (direction == OUTPUT) {
            return operation.getName() + "Response";
        }
        return operation.getName();
    }

    private static @NotNull Stream<Part> getParts(Definitions definitions, String serviceName, Direction direction, List<PortType> result) {
        return result.stream()
                .flatMap(portType -> {
                    final var bindings = getHeaderBindings(definitions, serviceName, portType);
                    return portType.getOperations().stream()
                            .flatMap(op -> {
                                final var headerParts = getHeaderPartElements(bindings, op, direction);
                                return op.getMessagesByDirection(direction).stream()
                                        .map(message -> getBodyPart(message, headerParts));
                            });
                })
                .filter(Objects::nonNull); // Message can have no body part.
    }

    /**
     * Document style only uses one part for the body: the first one not bound as {@code soap:header}.
     *
     * @return the body part, or null if the message has none
     */
    private static Part getBodyPart(Message message, Set<Element> headerParts) {
        return message.getParts().stream()
                .filter(part -> !headerParts.contains(part.getElement()))
                .findFirst()
                .orElse(null);
    }

    private record PortTypesByStyle(List<PortType> portTypesRPC, List<PortType> portTypesDocument) {
    }

}