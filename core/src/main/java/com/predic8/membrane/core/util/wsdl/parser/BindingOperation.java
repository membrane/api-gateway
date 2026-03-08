package com.predic8.membrane.core.util.wsdl.parser;

import org.w3c.dom.*;

public class BindingOperation extends WSDLElement{

    private final String soapAction;

    public BindingOperation(WSDLParserContext ctx, Node node) {
        super(node);
        soapAction = node.getAttributes().getNamedItem("soapAction").getNodeValue();
    }

    public String getSoapAction() {
        return soapAction;
    }
}
