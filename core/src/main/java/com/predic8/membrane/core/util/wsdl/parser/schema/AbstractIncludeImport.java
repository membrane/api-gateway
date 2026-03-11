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

import com.predic8.membrane.core.resolver.*;
import com.predic8.membrane.core.util.*;
import com.predic8.membrane.core.util.wsdl.parser.*;
import org.jetbrains.annotations.*;
import org.w3c.dom.*;

public abstract class AbstractIncludeImport extends WSDLElement {

    protected String schemaLocation;
    protected Schema schema;

    public AbstractIncludeImport(WSDLParserContext ctx, Node node) {
        super(ctx, node);
        schemaLocation = getSchemaLocation(node);
        schema = getSchema(ctx);
    }

    public Schema getSchema() {
        return schema;
    }

    protected Schema getSchema(WSDLParserContext ctx) {
        try {
            var resolved = resolve(ctx);

            // Check if the schema has already been imported or included
            if (ctx.visitedLocations().contains(resolved))
                return null;

            try (var is = ctx.resolver().resolve(resolved)) {
                return new Schema(ctx.basePath(resolved), WSDLParserUtil.parse(is));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String resolve(WSDLParserContext ctx) {
        return URIUtil.normalize(ResolverMap.combine(ctx.basePath(), schemaLocation));
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
