package com.predic8.membrane.core.util.wsdl.parser.schema;

import com.predic8.membrane.core.util.wsdl.parser.*;
import org.w3c.dom.*;

public class Include extends AbstractIncludeImport {

    public Include(WSDLParserContext ctx, Node node, Schema referensingSchema) {
        super(ctx,node,referensingSchema);

        // Copy all elements from the imported Schema into the importing
        referensingSchema.include(schema);
    }

    @Override
    protected void registerLocation(String normalizedLocation) {
        referensingSchema.getIncludes().add(this);
    }
}
