package com.predic8.membrane.core.util.wsdl.parser.schema;

import com.predic8.membrane.core.util.wsdl.parser.*;
import org.jetbrains.annotations.*;
import org.w3c.dom.*;

import java.util.*;

public class Import extends AbstractIncludeImport {

    private String namespace;

    public Import(WSDLParserContext ctx, Node node, Schema referensingSchema) {
        super(ctx, node, referensingSchema);

        namespace = getNamespace(node);

        // Schema is null when it is already imported from somewhere else
        if (schema == null)
            return;

        if (!Objects.equals(schema.getTargetNamespace(), namespace)) {
            throw new WSDLParserException("The namespace {%s} of the import does not match the targetNamespace of the imported schema {%s}.".formatted(namespace, schema.getTargetNamespace()));
        }
    }

    @Override
    protected Schema getSchema(WSDLParserContext ctx) {
        if (schemaLocation == null || schemaLocation.isEmpty()) {
            registerLocation("");
            return null;
        }
        return super.getSchema(ctx);
    }

    public void setSchema(Schema schema) {
        this.schema = schema;
    }

    @Override
    protected void registerLocation(String normalizedLocation) {
        referensingSchema.getImports().add(this);
    }

    private @NotNull String getNamespace(Node node) {
        return ((Element) node).getAttribute("namespace");
    }

    public String getNamespace() {
        return namespace;
    }
}
