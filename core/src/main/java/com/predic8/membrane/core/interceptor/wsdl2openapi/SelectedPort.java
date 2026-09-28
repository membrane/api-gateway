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
import com.predic8.membrane.core.util.wsdl.parser.*;

import java.util.List;
import java.util.Optional;

import static com.predic8.membrane.core.util.wsdl.parser.Definitions.SOAPVersion.SOAP_11;

/**
 * The one port of a WSDL a wsdl2openapi instance serves. A port fixes everything the plugin needs:
 * the address to call, the binding (SOAPAction, SOAP version) and, through it, the port type whose
 * operations are exposed. A WSDL without a service contributes just its port type, and that port
 * type's SOAP binding where it has one. Every lookup of an operation goes through the selected port, so that
 * operations, actions and address never come from different services.
 * <p>
 * Only for init(): the elements are views on the WSDL's Xerces DOM, which concurrent reads corrupt.
 *
 * @param service the selected service; {@code null} for a WSDL that declares none
 * @param name     the name of the port; for a WSDL without a service, the name of the port type
 * @param portType the port type whose operations are exposed
 * @param binding  the SOAP binding; {@code null} for a WSDL without a service whose port type has none
 * @param port     the port; {@code null} for a WSDL without a service
 */
record SelectedPort(Service service, String name, PortType portType, Binding binding, Port port) {

    /**
     * Selects the port by the configured service and port names, either of which may be
     * {@code null}. Without a service name, the WSDL must declare exactly one service. Without a
     * port name, the service's SOAP ports must all implement one port type, and the SOAP 1.1 port is
     * preferred: those are the SOAP 1.1 and 1.2 flavours of one interface many toolkits publish.
     * Without a service, the WSDL must declare exactly one port type.
     *
     * @throws ConfigurationException if no port, or not exactly one, matches
     */
    static SelectedPort select(Definitions definitions, String serviceName, String portName) {
        if (definitions.getServices().isEmpty()) {
            if (serviceName != null || portName != null) {
                throw new ConfigurationException("The WSDL declares no service, so neither a service nor a port can be selected.");
            }
            return ofPortType(definitions);
        }

        Service service = selectService(definitions, serviceName);
        List<SelectedPort> ports = service.getPorts().stream()
                .map(p -> new SelectedPort(service, p.getName(), p.getBinding().getPortType(), p.getBinding(), p))
                .toList();
        String where = "Service '%s'".formatted(service.getName());

        if (portName == null) {
            return choose(ports.stream().filter(p -> p.binding().isSoap()).toList(), where);
        }
        SelectedPort port = ports.stream().filter(p -> portName.equals(p.name())).findFirst()
                .orElseThrow(() -> new ConfigurationException("%s has no port '%s'. Available ports: %s"
                        .formatted(where, portName, names(ports))));
        if (!port.binding().isSoap()) {
            throw new ConfigurationException("Port '%s' of service '%s' is not bound to SOAP, so it cannot be called with SOAP messages."
                    .formatted(portName, service.getName()));
        }
        return port;
    }

    /**
     * An abstract WSDL: nothing to call, and its port type alone decides the operations. The port
     * type's SOAP binding, where there is one, still supplies the SOAPAction and the SOAP version.
     */
    private static SelectedPort ofPortType(Definitions definitions) {
        List<PortType> portTypes = definitions.getPortTypes();
        if (portTypes.isEmpty()) {
            throw new ConfigurationException("The WSDL declares neither a service nor a port type.");
        }
        if (portTypes.size() > 1) {
            throw new ConfigurationException("""
                    The WSDL declares no service and several port types: %s.
                    wsdl2openapi needs a WSDL with a single port type, or a service to select a port from.""".formatted(
                    portTypes.stream().map(PortType::getName).toList()));
        }
        PortType portType = portTypes.getFirst();
        List<Binding> bindings = definitions.getBindings(portType).stream()
                .filter(Binding::isSoap)
                .toList();
        Binding binding = bindings.stream().filter(b -> b.getSoapVersion() == SOAP_11).findFirst()
                .orElse(bindings.isEmpty() ? null : bindings.getFirst());
        return new SelectedPort(null, portType.getName(), portType, binding, null);
    }

    private static Service selectService(Definitions definitions, String serviceName) {
        List<Service> services = definitions.getServices();
        List<String> serviceNames = services.stream().map(Service::getName).toList();
        if (serviceName != null) {
            return definitions.getService(serviceName).orElseThrow(() -> new ConfigurationException(
                    "The WSDL has no service '%s'. Available services: %s".formatted(serviceName, serviceNames)));
        }
        if (services.size() > 1) {
            throw new ConfigurationException("""
                    The WSDL declares several services: %s.
                    Set service to the one this api exposes. To expose several, declare one api per service.""".formatted(serviceNames));
        }
        return services.getFirst();
    }

    /** The only candidate, or the SOAP 1.1 one among the flavours of a single port type. */
    private static SelectedPort choose(List<SelectedPort> candidates, String where) {
        if (candidates.isEmpty()) {
            throw new ConfigurationException("%s has no SOAP port.".formatted(where));
        }
        long portTypes = candidates.stream().map(p -> p.portType().getName()).distinct().count();
        if (portTypes > 1) {
            throw new ConfigurationException("""
                    %s has SOAP ports of different port types: %s.
                    Set port to the one this api exposes. To expose several, declare one api per port.""".formatted(where, names(candidates)));
        }
        return candidates.stream().filter(p -> p.binding().getSoapVersion() == SOAP_11).findFirst()
                .orElse(candidates.getFirst());
    }

    private static List<String> names(List<SelectedPort> ports) {
        return ports.stream().map(SelectedPort::name).toList();
    }

    /**
     * The address of the port; {@code null} for a WSDL without a service, or a port without an
     * address element. Read only for the selected port, so that a port nobody selected cannot fail
     * the configuration.
     */
    String address() {
        if (port == null) return null;
        return port.findAddress().map(Address::getLocation).orElse(null);
    }

    /** The operations of the port type the port implements, in document order, unnamed ones included. */
    List<Operation> operations() {
        return portType.getOperations();
    }

    Optional<Operation> findOperation(String operationName) {
        return portType.findOperation(operationName);
    }

    /** The binding's operation of that name; empty if there is no binding, or it does not cover the operation. */
    Optional<BindingOperation> findBindingOperation(String operationName) {
        if (binding == null) return Optional.empty();
        return binding.findBindingOperation(operationName);
    }

    /** The SOAP version the port's binding speaks: the one requests are sent in. SOAP 1.1 without a binding. */
    Definitions.SOAPVersion soapVersion() {
        return binding != null ? binding.getSoapVersion() : SOAP_11;
    }
}
