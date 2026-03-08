package com.predic8.membrane.core.util.wsdl.parser;

import org.w3c.dom.*;

public class WSDLElement {
    private Element element;
    private String name;

    public WSDLElement(Node node) {
        if (!(node instanceof Element element)) {
            throw new RuntimeException("Not an element: " + node.getClass());
        }
        this.element = element;
        var nameNode = element.getAttributes().getNamedItem("name");
        if (nameNode != null) {
            name = nameNode.getNodeValue();
        }
    }

    public String getName() {
        return name;
    }

    public Element getDefinitions() {
        return element.getOwnerDocument().getDocumentElement();
    }

}
