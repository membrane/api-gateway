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
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

import static com.predic8.membrane.core.util.wsdl.parser.Definitions.SOAPVersion.SOAP_11;
import static com.predic8.membrane.core.util.wsdl.parser.Definitions.SOAPVersion.UNKNOWN;

/**
 * The one port of a WSDL a wsdl2openapi instance serves. A port fixes everything the plugin needs:
 * the address to call, the binding (SOAPAction, SOAP version) and, through it, the port type whose
 * operations are exposed. Every lookup of an operation goes through the selected port, so that
 * operations, actions and address never come from different services.
 * <p>
 * Only for init(): the elements are views on the WSDL's Xerces DOM, which concurrent reads corrupt.
 *
 * @param service the selected service; {@code null} for a WSDL that declares none
 * @param name    the name of the port; for a WSDL without a service, the name of the binding
 * @param port    the port; {@code null} for a WSDL without a service
 */
record SelectedPort(Service service, String name, Binding binding, Port port) {

    /**
     * Selects the port by the configured service and port names, either of which may be
     * {@code null}. Without a service name, the WSDL must declare exactly one service. Without a
     * port name, the service's SOAP ports must all implement one port type, and the SOAP 1.1 port is
     * preferred: those are the SOAP 1.1 and 1.2 flavours of one interface many toolkits publish.
     *
     * @throws ConfigurationException if no port, or not exactly one, matches
     */
    static SelectedPort select(Definitions definitions, String serviceName, String portName) {
        if (definitions.getServices().isEmpty()) {
            if (serviceName != null || portName != null) {
                throw new ConfigurationException("The WSDL declares no service, so neither a service nor a port can be selected.");
            }
            // An abstract WSDL: nothing to call, but the bindings still describe the operations.
            return choose(definitions.getBindings().stream()
                    .filter(SelectedPort::isSoap)
                    .map(b -> new SelectedPort(null, b.getName(), b, null))
                    .toList(), "The WSDL");
        }

        Service service = selectService(definitions, serviceName);
        List<SelectedPort> ports = service.getPorts().stream()
                .map(p -> new SelectedPort(service, p.getName(), p.getBinding(), p))
                .toList();
        String where = "Service '%s'".formatted(service.getName());

        if (portName == null) {
            return choose(ports.stream().filter(p -> isSoap(p.binding())).toList(), where);
        }
        SelectedPort port = ports.stream().filter(p -> portName.equals(p.name())).findFirst()
                .orElseThrow(() -> new ConfigurationException("%s has no port '%s'. Available ports: %s"
                        .formatted(where, portName, names(ports))));
        if (!isSoap(port.binding())) {
            throw new ConfigurationException("Port '%s' of service '%s' is not bound to SOAP, so it cannot be called with SOAP messages."
                    .formatted(portName, service.getName()));
        }
        return port;
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
        long portTypes = candidates.stream().map(p -> p.binding().getPortType().getName()).distinct().count();
        if (portTypes > 1) {
            throw new ConfigurationException("""
                    %s has SOAP ports of different port types: %s.
                    Set port to the one this api exposes. To expose several, declare one api per port.""".formatted(where, names(candidates)));
        }
        return candidates.stream().filter(p -> p.binding().getSoapVersion() == SOAP_11).findFirst()
                .orElse(candidates.getFirst());
    }

    private static boolean isSoap(Binding binding) {
        return binding.getSoapVersion() != UNKNOWN;
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
        try {
            return port.getAddress().getLocation();
        } catch (NoSuchElementException e) {
            return null;
        }
    }

    /** The operations of the port type the port implements, in document order, unnamed ones included. */
    List<Operation> operations() {
        return binding.getPortType().getOperations();
    }

    Optional<Operation> findOperation(String operationName) {
        return operations().stream().filter(op -> Objects.equals(operationName, op.getName())).findFirst();
    }

    /** The binding's operation of that name; empty if the binding does not cover it. */
    Optional<BindingOperation> findBindingOperation(String operationName) {
        return binding.getBindingOperations().stream()
                .filter(bo -> Objects.equals(operationName, bo.getName()))
                .findFirst();
    }

    /** The SOAP version the port's binding speaks: the one requests are sent in. */
    Definitions.SOAPVersion soapVersion() {
        return binding.getSoapVersion();
    }
}
