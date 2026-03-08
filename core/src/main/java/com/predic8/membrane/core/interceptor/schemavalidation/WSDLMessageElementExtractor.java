package com.predic8.membrane.core.interceptor.schemavalidation;

import com.predic8.membrane.core.util.wsdl.parser.*;
import com.predic8.membrane.core.util.wsdl.parser.Operation.*;
import org.jetbrains.annotations.*;
import org.slf4j.*;

import javax.xml.namespace.*;
import java.util.*;
import java.util.stream.*;

import static com.predic8.membrane.core.util.wsdl.parser.Binding.Style.RPC;
import static com.predic8.membrane.core.util.wsdl.parser.Operation.Direction.INPUT;
import static com.predic8.membrane.core.util.wsdl.parser.Operation.Direction.OUTPUT;
import static java.util.stream.Collectors.*;

public class WSDLMessageElementExtractor {

    private static final Logger log = LoggerFactory.getLogger(WSDLMessageElementExtractor.class);

    public static Set<QName> getPossibleRequestElements(Definitions definitions, String serviceName) {
        return getPossibleElements(definitions, INPUT, serviceName);
    }

    public static Set<QName> getPossibleResponseElements(Definitions definitions, String serviceName) {
        return getPossibleElements(definitions, OUTPUT, serviceName);
    }

    public static Set<QName> getPossibleElements(Definitions definitions, Direction direction, String serviceName) {
        PortTypesByStyle portTypes;
        if (definitions.getServices().isEmpty()) {
            portTypes = new PortTypesByStyle(Collections.emptyList(), definitions.getPortTypes());
        } else {
            portTypes = getPortTypesByStyle(definitions, serviceName);
        }

        var operationNamesRPC = portTypes.portTypesRPC().stream().map(pt -> pt.getOperations())
                .flatMap(Collection::stream)
                .map(op -> new QName(definitions.getTargetNamespace(), getElementNameRPC(op,direction)))
                                         .collect(toSet());


        Set<QName> namesDocumentStyle = getParts(direction, portTypes.portTypesDocument())
                .map(part -> part.getElementQName())
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        namesDocumentStyle.addAll(operationNamesRPC);

        return namesDocumentStyle;
    }

    private static @NotNull PortTypesByStyle getPortTypesByStyle(Definitions definitions, String serviceName) {
        List<PortType> portTypesRPC = new ArrayList<>();
        List<PortType> portTypesDocument = new ArrayList<>();

        for (var binding : getBindings(definitions, serviceName)) {
            if (binding.getStyle() == RPC) {
                portTypesRPC.add(binding.getPortType());
            }
            portTypesDocument.add(binding.getPortType());
        }
        return new PortTypesByStyle(portTypesRPC, portTypesDocument);
    }

    private record PortTypesByStyle(List<PortType> portTypesRPC, List<PortType> portTypesDocument) {
    }

    private static @NotNull List<Binding> getBindings(Definitions definitions, String serviceName) {
        List<Service> services;
        if (serviceName != null) {
            services = List.of(definitions.getService(serviceName));
        } else {
            services = definitions.getServices();
        }
        return services.stream().flatMap(s -> s.getPorts().stream())
            .map(port -> port.getBinding()).toList();
    }

    private static String getElementNameRPC(Operation operation, Direction direction) {
        if (direction == INPUT) {
            return operation.getName();
        }
        return operation.getName() + "Response";
    }

    private static @NotNull Stream<Part> getParts(Direction direction, List<PortType> result) {
        return result.stream().map(pt -> pt.getOperations())
                .flatMap(Collection::stream)
                .map(op -> op.getMessagesByDirection(direction))
                .flatMap(Collection::stream).toList().stream().map(message -> message.getPart());
    }

}