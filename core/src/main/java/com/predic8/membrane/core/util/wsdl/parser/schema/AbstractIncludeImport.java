package com.predic8.membrane.core.util.wsdl.parser.schema;

import com.predic8.membrane.core.resolver.*;
import com.predic8.membrane.core.util.wsdl.parser.*;
import org.w3c.dom.*;

import java.io.*;

public abstract class AbstractIncludeImport extends WSDLElement {

    protected String schemaLocation;

    public AbstractIncludeImport(WSDLParserContext ctx, Node node) {
        super(node);
        var e = (Element) node;
        schemaLocation = e.getAttribute("schemaLocation");
    }

    protected Schema getSchema(WSDLParserContext ctx) {
        try {
            var combined = ResolverMap.combine(ctx.getBasePath(), schemaLocation);
            InputStream is = ctx.getResolver().resolve(combined);
            return new Schema(ctx.basePath(combined), WSDLParserUtil.parse(is));
        } catch (ResourceRetrievalException e) {
            throw new RuntimeException(e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public String getSchemaLocation() {
        return schemaLocation;
    }
}
