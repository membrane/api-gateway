package com.predic8.membrane.core.openapi.util;

import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.media.*;

public class SchemaUtil {

    public static Schema getSchemaFromRef(OpenAPI api, Schema schema) {

        // could be removed later. Only to debug.
        if (schema.get$ref() == null)
            return null;

        ObjectHolder<Schema> oh = new ObjectHolder();
        api.getComponents().getSchemas().forEach((schemaName, refSchema) -> {
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
