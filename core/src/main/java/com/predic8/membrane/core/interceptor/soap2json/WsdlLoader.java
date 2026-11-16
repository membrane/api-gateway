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

import com.predic8.membrane.core.router.Router;
import com.predic8.membrane.core.util.ConfigurationException;
import com.predic8.membrane.core.util.wsdl.parser.Definitions;
import com.predic8.membrane.core.util.wsdl.parser.WSDLParserException;

import java.net.URI;
import java.net.URISyntaxException;

import static com.predic8.membrane.core.resolver.ResolverMap.combine;
import static com.predic8.membrane.core.util.wsdl.parser.Definitions.parse;

/**
 * Loads the WSDL an interceptor is configured with and selects the port it works on — the same
 * way, and with the same configuration errors, for soap2Json, json2Soap and wsdl2openapi.
 */
public final class WsdlLoader {

    private WsdlLoader() {}

    /**
     * Parses the WSDL, resolving {@code wsdl} against the location of the configuration that
     * declares the interceptor.
     *
     * @throws ConfigurationException if the WSDL cannot be read or parsed
     */
    public static Definitions parseWsdl(Router router, String baseLocation, String wsdl) {
        try {
            final var resolvedWsdl = combine(router.getConfiguration().getUriFactory(), baseLocation, wsdl);
            return parse(router.getResolverMap(), resolvedWsdl);
        } catch (Exception e) {
            throw new ConfigurationException("Cannot parse WSDL '%s': %s".formatted(wsdl, e.getMessage()), e);
        }
    }

    /**
     * Selects the port by the configured service and port names — see {@link SelectedPort#select}.
     *
     * @throws ConfigurationException if no port, or not exactly one, matches
     */
    public static SelectedPort selectPort(Definitions definitions, String wsdl, String service, String port) {
        try {
            return SelectedPort.select(definitions, service, port);
        } catch (WSDLParserException e) {
            throw new ConfigurationException("Cannot select a port of WSDL '%s': %s".formatted(wsdl, e.getMessage()), e);
        }
    }

    /**
     * Both SOAP versions require the action to be a URI (SOAP 1.1 section 6.1.1, RFC 3902), which
     * is what lets it go between quotes unescaped: a URI cannot contain a quote or a backslash.
     *
     * @throws ConfigurationException if the binding gives the operation an action that is not a URI
     */
    public static void validateSoapAction(SelectedPort port, String wsdl, String operationName) {
        final var action = port.soapAction(operationName);
        if (action == null || action.isEmpty()) return;
        try {
            new URI(action);
        } catch (URISyntaxException e) {
            throw new ConfigurationException("""
                    The WSDL '%s' gives operation '%s' the SOAP action '%s', which is not a URI.
                    SOAP requires the action to be a URI. Percent-encode characters such as quotes, backslashes or spaces.""".formatted(wsdl, operationName, action), e);
        }
    }
}
