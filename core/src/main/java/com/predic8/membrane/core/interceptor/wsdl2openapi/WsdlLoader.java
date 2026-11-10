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

import com.predic8.membrane.core.router.Router;
import com.predic8.membrane.core.util.ConfigurationException;
import com.predic8.membrane.core.util.wsdl.parser.Definitions;
import com.predic8.membrane.core.util.wsdl.parser.WSDLParserException;

import static com.predic8.membrane.core.resolver.ResolverMap.combine;
import static com.predic8.membrane.core.util.wsdl.parser.Definitions.parse;

/**
 * Loads the WSDL an interceptor is configured with and selects the port it works on — the same
 * way, and with the same configuration errors, for every interceptor of this package.
 */
final class WsdlLoader {

    private WsdlLoader() {}

    /**
     * Parses the WSDL, resolving {@code wsdl} against the location of the configuration that
     * declares the interceptor.
     *
     * @throws ConfigurationException if the WSDL cannot be read or parsed
     */
    static Definitions parseWsdl(Router router, String baseLocation, String wsdl) {
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
    static SelectedPort selectPort(Definitions definitions, String wsdl, String service, String port) {
        try {
            return SelectedPort.select(definitions, service, port);
        } catch (WSDLParserException e) {
            throw new ConfigurationException("Cannot select a port of WSDL '%s': %s".formatted(wsdl, e.getMessage()), e);
        }
    }
}
