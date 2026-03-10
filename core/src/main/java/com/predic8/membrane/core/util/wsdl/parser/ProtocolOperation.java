package com.predic8.membrane.core.util.wsdl.parser;

import org.w3c.dom.*;

/**
 * e.g., wsdl:binding/wsdl:operation/s11:operation
 */
public class ProtocolOperation extends WSDLElement {
    public ProtocolOperation(WSDLParserContext ctx, Node node) {
        super(ctx, node);
    }

    public String getSoapAction() {
        return getAttribute("soapAction");
    }
}
