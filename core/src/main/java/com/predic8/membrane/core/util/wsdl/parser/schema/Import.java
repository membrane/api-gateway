package com.predic8.membrane.core.util.wsdl.parser.schema;

import com.predic8.membrane.core.util.wsdl.parser.*;
import org.w3c.dom.*;

public class Import extends AbstractIncludeImport {

    private final String namespace;
    private final Schema schema;

    public Import(WSDLParserContext ctx,Node node) {
        super(ctx,node);
        var e = (Element) node;
        namespace = e.getAttribute("namespace");

        schema=getSchema(ctx);
        ctx.getDefinitions().addImportedSchema(schema);
    }
}
