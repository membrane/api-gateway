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

package com.predic8.membrane.core.util.wsdl.parser;

import com.predic8.membrane.core.util.wsdl.parser.Definitions.SOAPVersion;
import org.w3c.dom.Node;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class Binding extends WSDLElement {

    public enum Style {
        RPC, DOCUMENT;

        public static Style fromString(String style) {
            return switch (style) {
                case "rpc" -> RPC;
                default -> DOCUMENT;
            };
        }
    }

    public Binding(WSDLParserContext ctx, Node node) {
        super(ctx, node);
    }

    public Style getStyle() {
        return getBindingStyle().getStyle();
    }

    public SOAPVersion getSoapVersion() {
        return getBindingStyle().getSoapVersion();
    }

    /** The transport of WSDL 1.1 and its SOAP 1.2 binding: HTTP. */
    public static final String SOAP_HTTP_TRANSPORT = "http://schemas.xmlsoap.org/soap/http";

    /** The identifier of SOAP 1.2's own HTTP binding, which some WSDLs use as the transport. */
    public static final String SOAP12_HTTP_BINDING = "http://www.w3.org/2003/05/soap/bindings/HTTP/";

    /** Not defined by any specification, but found in SOAP 1.2 bindings in the wild. */
    public static final String SOAP12_HTTP_TRANSPORT_NONSTANDARD = "http://schemas.xmlsoap.org/soap12/http";

    private static final Set<String> HTTP_TRANSPORTS = Set.of(SOAP_HTTP_TRANSPORT, SOAP12_HTTP_BINDING, SOAP12_HTTP_TRANSPORT_NONSTANDARD);

    /** The transport URI of the binding; empty if it declares none. */
    public String getTransport() {
        return getBindingStyle().getTransport();
    }

    /**
     * Whether the binding sends SOAP over HTTP. WSDL 1.1 requires the transport, but a binding
     * without one is taken to mean HTTP, as hand-written WSDLs often leave it out. For the same
     * reason, a nonstandard SOAP 1.2 HTTP transport URI is accepted too.
     */
    public boolean isSoapOverHttp() {
        if (!isSoap()) return false;
        String transport = getTransport();
        return transport.isEmpty() || HTTP_TRANSPORTS.contains(transport);
    }

    /** Whether the binding binds to SOAP 1.1 or SOAP 1.2. */
    public boolean isSoap() {
        return getSoapVersion() != SOAPVersion.UNKNOWN;
    }

    /** The binding's operation of that name; empty if the binding does not cover it. */
    public Optional<BindingOperation> findBindingOperation(String name) {
        return getBindingOperations().stream()
                .filter(bo -> Objects.equals(name, bo.getName()))
                .findFirst();
    }

    public List<BindingOperation> getBindingOperations() {
        return instantiateWSDLChildren("operation", BindingOperation.class);
    }

    private BindingStyle getBindingStyle() {
        var binding = instantiateChildren("binding", BindingStyle.class);
        if (binding.isEmpty())
            throw new WSDLParserException("No bindingStyle found for binding: " + getName());
        return binding.getFirst();
    }

    public PortType getPortType() {
        return findPortType().orElseThrow(() -> new WSDLParserException("No portType found for binding: " + getName()));
    }

    /**
     * The port type the binding's type names; empty if it names none of this WSDL's. The type is a
     * QName, and all port types of the WSDL are in its target namespace.
     */
    Optional<PortType> findPortType() {
        var type = resolveQName(getAttribute("type"));
        if (!Objects.equals(ctx.definitions().getTargetNamespace(), type.getNamespaceURI()))
            return Optional.empty();
        return ctx.definitions().getPortTypes().stream()
                .filter(pt -> type.getLocalPart().equals(pt.getName()))
                .findFirst();
    }
}
