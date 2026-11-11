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

import com.predic8.membrane.core.resolver.Resolver;
import com.predic8.membrane.core.util.wsdl.parser.*;
import org.w3c.dom.*;

import java.util.*;

import static com.predic8.membrane.core.util.URIUtil.getNormalizedAbsolutePathOrUri;

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

    /**
     * Parses a standalone XML Schema document together with everything it imports and includes.
     * Locations the document refers to are resolved against {@code location}.
     *
     * @throws WSDLParserException if the document is not an {@code xsd:schema}
     */
    public static Schema parse(Resolver resolver, String location) throws Exception {
        final Element root;
        try (var is = resolver.resolve(location)) {
            root = WSDLParserUtil.parse(is);
        }
        if (!isXSDElementWithName(root, "schema")) {
            throw new WSDLParserException("The document at %s is not an XML Schema: its root element is {%s}%s."
                    .formatted(location, root.getNamespaceURI(), root.getLocalName()));
        }
        // The document itself counts as visited, so that a schema importing it back does not load it twice.
        final var visited = new ArrayList<String>();
        visited.add(getNormalizedAbsolutePathOrUri(location));
        return new Schema(new WSDLParserContext(null, resolver, location, visited), root);
    }

    /**
     * Builds namespace -> schema-root-element list by BFS over imports and includes.
     * Uses identity-based dedup. A schema without targetNamespace is listed under the empty
     * namespace; one that was included has already taken on the namespace of the schema
     * including it (see {@link Include#adopt}).
     */
    public static Map<String, List<Element>> schemasByNamespace(List<Schema> schemas) {
        var map = new LinkedHashMap<String, List<Element>>();
        var queue = new ArrayDeque<>(schemas);
        var seen = Collections.newSetFromMap(new IdentityHashMap<>());
        seen.addAll(schemas);
        while (!queue.isEmpty()) {
            var schema = queue.poll();
            map.computeIfAbsent(schema.getTargetNamespace(), k -> new ArrayList<>()).add(schema.getSchemaElement());
            for (var imp : schema.getImports()) {
                var imported = imp.getSchema();
                if (imported != null && imported.getTargetNamespace() != null && seen.add(imported)) {
                    queue.add(imported);
                }
            }
            for (var inc : schema.getIncludes()) {
                var included = inc.getSchema();
                if (included != null && seen.add(included)) {
                    queue.add(included);
                }
            }
        }
        return map;
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