package com.predic8.membrane.core.util.wsdl.parser.schema;

import com.predic8.membrane.core.util.wsdl.parser.*;
import org.jetbrains.annotations.*;
import org.w3c.dom.*;

public class Import extends AbstractIncludeImport {

    private String namespace;

    public Import(WSDLParserContext ctx, Node node, Schema referensingSchema) {
        super(ctx, node, referensingSchema);

        // Schema is null when it is already imported from somewhere else.
        if (schema == null)
            return;

        this.referensingSchema = referensingSchema;

        namespace = getNamespace(node);

        if (!schema.getTargetNamespace().equals(namespace)) {
            throw new WSDLParserException("The namespace {%s} of the import does not match the targetNamespace of the imported schema {%s}.".formatted(namespace, schema.getTargetNamespace()));
        }
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
