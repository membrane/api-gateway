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
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.namespace.QName;
import java.util.*;
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
        var result = new HashSet<>(getElementQNameForDocumentStyle(direction, portTypes));
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

    private static @NotNull Set<QName> getElementQNameForDocumentStyle(Direction direction, PortTypesByStyle portTypes) {
        return getParts(direction, portTypes.portTypesDocument())
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

    private static @NotNull Stream<Part> getParts(Direction direction, List<PortType> result) {
        return result.stream().map(PortType::getOperations)
                .flatMap(Collection::stream)
                .map(op -> op.getMessagesByDirection(direction))
                .flatMap(Collection::stream)
                .map(Message::getPart)
                .filter(Objects::nonNull); // Message can have no parts.
    }

    private record PortTypesByStyle(List<PortType> portTypesRPC, List<PortType> portTypesDocument) {
    }

}