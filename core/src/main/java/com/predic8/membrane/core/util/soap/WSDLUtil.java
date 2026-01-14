/* Copyright 2025 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.util.soap;

import com.predic8.membrane.core.proxies.*;
import com.predic8.wsdl.*;
import org.slf4j.*;

import javax.xml.namespace.*;
import java.util.*;
import java.util.regex.*;

import static com.predic8.membrane.core.Constants.*;
import static java.util.regex.Matcher.quoteReplacement;

public class WSDLUtil {

    private static final Logger log = LoggerFactory.getLogger(WSDLUtil.class.getName());

    private static final Pattern relativePathPattern = Pattern.compile("^\\./[^/?]*\\?");

    public static String rewriteRelativeWsdlPath(String path, String replacementName) {
        return relativePathPattern
                .matcher(path)
                .replaceAll(quoteReplacement("./%s?".formatted(replacementName)));
    }

    /**
     * Retrieves a SOAP port from the given list of ports. This method first attempts to find a
     * port using the SOAP 1.1 namespace and, if not found, tries using the SOAP 1.2 namespace.
     *
     * @param ports the list of available ports to search for a SOAP/1.1 or SOAP/1.2 port
     * @return the first matching SOAP port if found
     * @throws IllegalArgumentException if no SOAP/1.1 or SOAP/1.2 port is found
     */
    public static Port getPort(List<Port> ports) {
        Port port = getPortByNamespace(ports, WSDL_SOAP11_NS);
        if (port == null)
            port = getPortByNamespace(ports, WSDL_SOAP12_NS);
        if (port != null)
            return port;
        throw new IllegalArgumentException("No SOAP/1.1 or SOAP/1.2 ports found in WSDL.");
    }

    private static Port getPortByNamespace(List<Port> ports, String namespace) {
        for (Port port : ports) {
            try {
                if (port.getBinding() == null)
                    continue;
                if (port.getBinding().getBinding() == null)
                    continue;
                AbstractBinding binding = port.getBinding().getBinding();
                if (!"http://schemas.xmlsoap.org/soap/http".equals(binding.getProperty("transport")))
                    continue;
                if (!namespace.equals(((QName) binding.getElementName()).getNamespaceURI()))
                    continue;
                return port;
            } catch (Exception e) {
                log.warn("Error inspecting WSDL port binding.", e);
            }
        }
        return null;
    }
}
