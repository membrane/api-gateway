package com.predic8.membrane.core.util.wsdl.parser.schema;

import com.predic8.membrane.core.util.wsdl.parser.*;
import org.w3c.dom.*;

public class SchemaElement extends WSDLElement {

    WSDLParserContext ctx;

    public SchemaElement(WSDLParserContext ctx,Node node) {
        super(ctx,node);
        this.ctx = ctx;
    }

    @Override
    public String toString() {
        return "SchemaElement{name=%s}".formatted(getName());
    }
}
