package com.predic8.membrane.core.interceptor.schemavalidation;

import com.predic8.membrane.core.util.wsdl.parser.Definitions.*;
import org.w3c.dom.*;

import java.util.*;

import static com.predic8.membrane.annot.Constants.*;
import static com.predic8.membrane.core.util.wsdl.parser.Definitions.SOAPVersion.*;

public class WSDLSOAPVersionExtractor {

    public static Set<SOAPVersion> getSOAPVersions(Element wsdl) {
        var result = new HashSet<SOAPVersion>();
        var ports = wsdl.getElementsByTagNameNS(WSDL11_NS, "port");

        // No port element, assume abstract WSDL, all versions could be valid
        if (ports.getLength() == 0) {
            return Set.of(SOAP_11, SOAP_12);
        }

        for (int i = 0; i < ports.getLength(); i++) {

            Element port = (Element) ports.item(i);
            NodeList children = port.getChildNodes();

            for (int j = 0; j < children.getLength(); j++) {

                Node n = children.item(j);
                if (n.getNodeType() != Node.ELEMENT_NODE)
                    continue;

                String ns = n.getNamespaceURI();

                if ("http://schemas.xmlsoap.org/wsdl/soap/".equals(ns)) {
                    result.add(SOAP_11);
                }

                if ("http://schemas.xmlsoap.org/wsdl/soap12/".equals(ns)) {
                    result.add(SOAP_12);
                }
            }
        }
        return result;
    }
}
