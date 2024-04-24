/*
 *  Copyright 2022 predic8 GmbH, www.predic8.com
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.predic8.membrane.core.openapi.util;

import com.predic8.membrane.core.openapi.*;
import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.media.*;

import java.util.*;

@SuppressWarnings("rawtypes")
public class SchemaUtil {

    public static Schema getSchemaFromRef(OpenAPI api, Schema schema) {

        Components components = api.getComponents();
        if (components == null)
            throw new OpenAPIParsingException("OpenAPI with title %s has no #/components field.");

        Map<String, Schema> schemas = components.getSchemas();
        if(schemas == null)
            throw new OpenAPIParsingException("OpenAPI with title %s has no #/components/schemas field.");

        ObjectHolder<Schema> oh = new ObjectHolder<>();
        schemas.forEach((schemaName, refSchema) -> {
            if (schemaName.equals(getSchemaNameFromRef(schema))) {
                oh.setValue(refSchema);
            }

        });
        return oh.getValue();
    }

    public static String getSchemaNameFromRef(Schema schema) {
        return Utils.getComponentLocalNameFromRef(schema.get$ref());
    }
}
