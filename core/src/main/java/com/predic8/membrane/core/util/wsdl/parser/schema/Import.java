/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.util.wsdl.parser.schema;

import com.predic8.membrane.core.util.wsdl.parser.*;
import org.jetbrains.annotations.*;
import org.slf4j.*;
import org.w3c.dom.*;

import java.util.*;

public class Import extends AbstractIncludeImport {

    private static final Logger log = LoggerFactory.getLogger(Definitions.class);

    private final String namespace;

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

    /**
     * Imports an import in a schema that is embedded in the WSDL definition.
     * Function is typically called from Definitions.importEmbeddedSchemas().
     * @param definitions The WSL that contains the embedded schema.
     */
    public void importEmbeddedSchema(Definitions definitions) {
        if (isSchemaLocationMissing())
            return;

        log.debug("Importing embedded schema with namespace: {}", namespace);
        definitions.getEmbeddedSchema(namespace).ifPresent(s -> schema = s);
    }

    private boolean isSchemaLocationMissing() {
        return schemaLocation != null && !schemaLocation.isEmpty();
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
