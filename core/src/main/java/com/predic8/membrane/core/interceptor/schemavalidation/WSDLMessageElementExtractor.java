package com.predic8.membrane.core.interceptor.schemavalidation;

import com.predic8.membrane.core.util.wsdl.parser.*;
import com.predic8.membrane.core.util.wsdl.parser.Operation.*;
import org.jetbrains.annotations.*;

import javax.xml.namespace.*;
import java.util.*;
import java.util.stream.*;

import static com.predic8.membrane.core.util.wsdl.parser.Binding.Style.*;
import static com.predic8.membrane.core.util.wsdl.parser.Operation.Direction.*;
import static java.util.stream.Collectors.*;

public class WSDLMessageElementExtractor {

    public static Set<QName> getPossibleRequestElements(Definitions definitions, String serviceName) {
        return getPossibleElements(definitions, INPUT, serviceName);
    }

    public static Set<QName> getPossibleResponseElements(Definitions definitions, String serviceName) {
        return getPossibleElements(definitions, OUTPUT, serviceName);
    }

    public static Set<QName> getPossibleElements(Definitions definitions, Direction direction, String serviceName) {
        var portTypes = getTypesByStyle(definitions, serviceName);

        var operationNamesRPC = portTypes.portTypesRPC().stream().map(PortType::getOperations)
                .flatMap(Collection::stream)
                .filter(op -> !op.getMessagesByDirection(direction).isEmpty())
                .map(op -> new QName(definitions.getTargetNamespace(), getElementNameRPC(op, direction)))
                .collect(toSet());


        var namesDocumentStyle = getParts(direction, portTypes.portTypesDocument())
                .map(Part::getElementQName)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        namesDocumentStyle.addAll(operationNamesRPC);
        return namesDocumentStyle;
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
        if (direction == INPUT) {
            return operation.getName();
        }
        return operation.getName() + "Response";
    }

    private static @NotNull Stream<Part> getParts(Direction direction, List<PortType> result) {
        return result.stream().map(PortType::getOperations)
                .flatMap(Collection::stream)
                .map(op -> op.getMessagesByDirection(direction))
                .flatMap(Collection::stream).toList().stream().map(Message::getPart);
    }

    private record PortTypesByStyle(List<PortType> portTypesRPC, List<PortType> portTypesDocument) {
    }

}