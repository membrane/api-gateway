package com.predic8.membrane.core.util;

import com.predic8.membrane.core.*;
import com.predic8.wsdl.*;
import org.slf4j.*;

import javax.xml.namespace.*;
import java.util.*;

import static com.predic8.membrane.core.Constants.SoapVersion.*;
import static com.predic8.membrane.core.Constants.WSDL_SOAP11_NS;
import static com.predic8.membrane.core.Constants.WSDL_SOAP12_NS;
import static com.predic8.membrane.core.util.WSDLUtil.Direction.*;

public class WSDLUtil {

    static Logger log = LoggerFactory.getLogger(WSDLUtil.class.getName());

    /**
     * Searches a service in a WSDL. Does not consider namespaces
     *
     * @param definitions
     * @param serviceName local name component of the attribute definitions.service.@name
     * @return
     */
    public static Service getService(Definitions definitions, String serviceName) {
        List<Service> services = definitions.getServices().stream().filter(s -> s.getName().equals(serviceName)).toList();
        if (services.isEmpty()) {
            String msg = "No service found with name %s in WSDL with namespace %s".formatted(serviceName, definitions.getTargetNamespace());
            log.error(msg);
            throw new RuntimeException(msg);
        }
        return services.get(0);
    }

    public enum Direction {REQUEST, RESPONSE}

    ;

    /**
     * A schema can have lots of toplevel elements but not all elements are valid toplevel elements in SOAP requests.
     * Only the elements referenced from service->port->binding->porttype->msg->types-schema->element are valid
     * toplevel elements in SOAP requests.
     *
     * @param service service for that elements should be searched
     * @return Set of elements that are allowed in this service as soap elements
     */
    public static Set<QName> getPossibleSOAPElements(Service service, Direction direction) {
        Set<QName> elements = new HashSet<>();

        // @TODO Only for one port!
        List<Operation> operations = service.getPorts().get(0).getBinding().getPortType().getOperations();

        operations.forEach(operation -> {
            AbstractPortTypeMessage message = getMessage(direction, operation);

            // Does the Operation have an input/output message according to the direction parameter?
            if (message == null)
                return;

            List<Part> parts = message.getMessage().getParts();
            parts.forEach(part -> {
                elements.add(XMLUtil.groovyToJavaxQName(part.getElement().getQname()));
            });
        });

        return elements;
    }

    /**
     * SOAP version of a WSDL port
     * @param port of definitions.service object
     * @return SOAP version enum
     */
    public static Constants.SoapVersion getSOAPVersion(Port port) {
        if (port.getAddress().getElementName() instanceof QName qn) {
            return switch (qn.getNamespaceURI()) {
                case WSDL_SOAP11_NS -> SOAP11;
                case WSDL_SOAP12_NS -> SOAP12;
                default -> UNKNOWN;
            };
        }
        return UNKNOWN;

    }

    private static AbstractPortTypeMessage getMessage(Direction direction, Operation operation) {
        if (direction == REQUEST)
            return operation.getInput();
        return operation.getOutput();
    }
}
