package com.predic8.membrane.core.util.wsdl.parser;

import org.w3c.dom.*;

import java.util.*;

import static com.predic8.membrane.annot.Constants.*;

public class Service extends WSDLElement {

    public static final String PORT = "port";
    private final List<Port> ports;

    public Service(WSDLParserContext ctx, Node element) {
        super(ctx,element);
        ports = getPorts(element);
        ctx.getDefinitions().services.add(this);
    }

    public List<Port> getPorts() {
        return ports;
    }

    public List<Port> getPorts(Node service) {
        var ports = new ArrayList<Port>();
        var children = service.getChildNodes();

        for (int i = 0; i < children.getLength(); i++) {
            var node = children.item(i);
            if (node instanceof Element e &&
                PORT.equals(e.getLocalName()) && WSDL11_NS.equals(e.getNamespaceURI())) {
                ports.add(new Port(ctx,e));
            }
        }
        return ports;
    }
}
