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

    private final String targetNamespace;

    /**
     * DOM Element of the schema as read from the WSDL.
     */
    private final Element schema;

    private final List<SchemaElement> schemaElements;
    private List<Import> imports = new ArrayList<>();
    private List<Include> includes = new ArrayList<>();

    public Schema(WSDLParserContext ctx, Node node) {
        super(ctx, node);
        this.targetNamespace = getTargetNamespace(node);
        this.schema = (Element) node;
        this.schemaElements = getSchemaElements(schema);
        parseImports(node);
        parseIncludes(node);
    }

    public String getTargetNamespace() {
        return targetNamespace;
    }

    public Element getSchemaElement() {
        return schema;
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

    private String getTargetNamespace(Node node) {
        if (!(node instanceof Element schema)) {
            return null;
        }
        var tns = schema.getAttribute("targetNamespace");
        return (tns.isEmpty()) ? null : tns;
    }

    private void parseIncludes(Node node) {
        includes = instantiateXSDChildElements(node,"include",Include.class);
        includes.forEach(i -> include(i.getSchema()));
    }

    private void parseImports(Node node) {
        imports = instantiateXSDChildElements(node,"import",Import.class);
    }

    private List<SchemaElement> getSchemaElements(Element schema) {
        return instantiateXSDChildElements(schema,"element", SchemaElement.class);
    }
}
