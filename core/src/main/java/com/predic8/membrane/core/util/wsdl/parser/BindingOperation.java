package com.predic8.membrane.core.util.wsdl.parser;

import org.w3c.dom.*;

public class BindingOperation extends WSDLElement{

     private WSDLParserContext ctx;

    private String name;
    private String soapAction;

    public BindingOperation(WSDLParserContext ctx, Node node) {
        super(node);
        this.ctx = ctx;
    }

    public String getName() {
        return name;
    }

    public String getSoapAction() {
        return soapAction;
    }
}
