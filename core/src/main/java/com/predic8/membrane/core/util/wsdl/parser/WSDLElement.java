package com.predic8.membrane.core.util.wsdl.parser;

import org.w3c.dom.*;

public class WSDLElement {

    protected final WSDLParserContext ctx;
    protected final Element element;
    protected String name;

    public WSDLElement(WSDLParserContext ctx,Node node) {
        this.ctx = ctx;
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
