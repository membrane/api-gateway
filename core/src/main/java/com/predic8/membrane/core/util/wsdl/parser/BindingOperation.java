package com.predic8.membrane.core.util.wsdl.parser;

import org.w3c.dom.*;

public class BindingOperation extends WSDLElement{

    private final String soapAction;

    public BindingOperation(WSDLParserContext ctx, Node node) {
        super(ctx,node);
        soapAction = getSoapAction(node);
    }

    private String getSoapAction(Node node) {
        Node n = node.getAttributes().getNamedItem("soapAction");
        if (n == null)
            return "";
        return n.getNodeValue();
    }

    public String getSoapAction() {
        return soapAction;
    }
}
