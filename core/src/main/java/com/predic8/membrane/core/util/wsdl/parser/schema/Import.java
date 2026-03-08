package com.predic8.membrane.core.util.wsdl.parser.schema;

import com.predic8.membrane.core.util.wsdl.parser.*;
import org.w3c.dom.*;

public class Import extends AbstractIncludeImport {

    private WSDLParserContext ctx;
    private String namespace;

    private Schema schema;

    public Import(WSDLParserContext ctx,Node node) {
        super(ctx,node);
        this.ctx = ctx;
        var e = (Element) node;
        namespace = e.getAttribute("namespace");

        schema=getSchema(ctx);
        ctx.getDefinitions().addImportedSchema(schema);
    }
}
