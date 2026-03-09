package com.predic8.membrane.core.util.wsdl.parser.schema;

import com.predic8.membrane.core.resolver.*;
import com.predic8.membrane.core.util.*;
import com.predic8.membrane.core.util.wsdl.parser.*;
import org.jetbrains.annotations.*;
import org.w3c.dom.*;

public abstract class AbstractIncludeImport extends WSDLElement {

    protected String schemaLocation;
    protected Schema referensingSchema;
    protected Schema schema;

    public AbstractIncludeImport(WSDLParserContext ctx, Node node, Schema referensingSchema) {
        super(ctx, node);
        this.referensingSchema = referensingSchema;
        schemaLocation = getSchemaLocation(node);
        schema = getSchema(ctx);
    }

    protected abstract void registerLocation(String normalizedLocation);

    public Schema getSchema() {
        return schema;
    }

    protected Schema getSchema(WSDLParserContext ctx) {
        try {
            var resolved = resolve(ctx);

            // Check if the schema has already been imported or included
            if (ctx.getVisitedLocations().contains(resolved))
                return null;

            registerLocation(resolved);

            try (var is = ctx.getResolver().resolve(resolved)) {
                return new Schema(ctx.basePath(resolved), WSDLParserUtil.parse(is));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String resolve(WSDLParserContext ctx) {
        return URIUtil.normalize(ResolverMap.combine(ctx.getBasePath(), schemaLocation));
    }

    public String getSchemaLocation() {
        return schemaLocation;
    }

    private static @NotNull String getSchemaLocation(Node node) {
        if (node instanceof Element e)
            return e.getAttribute("schemaLocation");
        return "";
    }
}
