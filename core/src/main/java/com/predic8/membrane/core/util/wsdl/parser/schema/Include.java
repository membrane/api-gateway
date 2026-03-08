package com.predic8.membrane.core.util.wsdl.parser.schema;

import com.predic8.membrane.core.util.wsdl.parser.*;
import org.jetbrains.annotations.*;
import org.w3c.dom.*;

public class Include extends AbstractIncludeImport {

    public Include(WSDLParserContext ctx, Node node, Schema schema) {
        super(ctx,node);
        this.schemaLocation = getSchemaLocation( node);

        schema.getSchemaElements().addAll(getSchema(ctx).getSchemaElements());
    }

    private static @NotNull String getSchemaLocation(Node node) {
        if (node instanceof Element e)
            return e.getAttribute("schemaLocation");
        return "";
    }
}
