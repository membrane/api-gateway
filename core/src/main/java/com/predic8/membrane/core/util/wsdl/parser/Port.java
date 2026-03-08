package com.predic8.membrane.core.util.wsdl.parser;

import org.w3c.dom.*;

import static com.predic8.membrane.annot.Constants.WSDL11_NS;
import static org.w3c.dom.Node.ELEMENT_NODE;

public class Port extends WSDLElement {

    private final Address address;
    private final Binding binding;

    public Port(WSDLParserContext ctx, Node node) {
        super(ctx,node);
        address = getAddress(node);
        binding = getBinding(node);

    }

    public Address getAddress() {
        return address;
    }

    public Binding getBinding() {
        return binding;
    }

    public Address getAddress(Node node) {
        var children = node.getChildNodes();

        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);

            if (child.getNodeType() == ELEMENT_NODE) {
                var localName = child.getLocalName();
                if ("address".equals(localName)) {
                    return new Address(ctx,child);
                }
            }
        }
        return null;
    }

    public Binding getBinding(Node node) {
        if (!(node instanceof Element port))
            return null;

        String bindingAttr = port.getAttribute("binding");
        if (bindingAttr.isEmpty())
            return null;

        var bindingQName = WSDLParserUtil.resolveQName(bindingAttr, port);
        var bindings = getDefinitions().getElementsByTagNameNS(WSDL11_NS, "binding");

        for (int i = 0; i < bindings.getLength(); i++) {
            Element binding = (Element) bindings.item(i);

            if (bindingQName.getLocalPart().equals(binding.getAttribute("name"))) {
                return new Binding(ctx, binding);
            }
        }

        return null;
    }
}
