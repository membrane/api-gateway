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
import org.w3c.dom.*;

import java.util.*;

public class Schema extends WSDLElement {

    // These values are read repeatably so they are stored
    private final List<SchemaElement> schemaElements;
    private final List<Import> imports;
    private final List<Include> includes;

    public Schema(WSDLParserContext ctx, Node node) {
        super(ctx, node);
        this.schemaElements = instantiateXSDChildren("element", SchemaElement.class);
        imports = instantiateXSDChildren("import",Import.class);
        includes = instantiateXSDChildren("include",Include.class);
        includes.forEach(i -> include(i.getSchema()));
    }

    public String getTargetNamespace() {
        return getAttribute("targetNamespace");
    }

    public Element getSchemaElement() {
        return element;
    }

    public List<SchemaElement> getSchemaElements() {
        return schemaElements;
    }

    public List<Import> getImports() {
        return imports;
    }

    public List<Include> getIncludes() {
        return includes;
    }

    /**
     * Take everything from the included schema and add it to the current schema.
     * At the moment only elements are added. More is not needed.
     * Later other features can be added as needed.
     *
     * @param includedSchema the schema whose elements are to be added to the current schema
     */
    public void include(Schema includedSchema) {
        if (includedSchema == null)
            return;

        schemaElements.addAll(includedSchema.getSchemaElements());
        imports.addAll(includedSchema.getImports());
    }
}