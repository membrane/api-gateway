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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.predic8.membrane.core.openapi.OpenAPIParsingException;
import com.predic8.membrane.shaded.io.swagger.v3.oas.models.Components;
import com.predic8.membrane.shaded.io.swagger.v3.oas.models.OpenAPI;
import com.predic8.membrane.shaded.io.swagger.v3.oas.models.media.Schema;

import java.util.Map;

import static com.predic8.membrane.core.openapi.util.Utils.getComponentLocalNameFromRef;

@SuppressWarnings("rawtypes")
public class SchemaUtil {

    /**
     * Looks up the schema referenced by {@code ref} under {@code #/components/schemas}.
     *
     * @return the referenced schema, or {@code null} if no schema with that name exists
     * @throws OpenAPIParsingException if the document has no {@code #/components} or
     *                                 {@code #/components/schemas} section
     */
    public static Schema getSchemaFromRef(OpenAPI api, String ref) {

        Components components = api.getComponents();
        if (components == null)
            throw new OpenAPIParsingException("OpenAPI with title %s has no #/components field.");

        Map<String, Schema> schemas = components.getSchemas();
        if(schemas == null)
            throw new OpenAPIParsingException("OpenAPI with title %s has no #/components/schemas field.");

        ObjectHolder<Schema> oh = new ObjectHolder<>();
        schemas.forEach((schemaName, refSchema) -> {
            if (schemaName.equals(getComponentLocalNameFromRef(ref))) {
                oh.setValue(refSchema);
            }

        });
        return oh.getValue();
    }

    /**
     * Returns the local component name of the schema's {@code $ref}, e.g. {@code Customer} for
     * {@code #/components/schemas/Customer}.
     */
    public static String getSchemaNameFromRef(Schema schema) {
        return getComponentLocalNameFromRef(schema.get$ref());
    }

    /**
     * Resolves a {@code $ref} to the referenced schema; returns {@code schema} unchanged if it has no ref.
     */
    public static Schema resolveRef(OpenAPI api, Schema schema) {
        if (schema == null || schema.get$ref() == null)
            return schema;
        return getSchemaFromRef(api, schema.get$ref());
    }

    /**
     * Returns the effective type of a schema, handling both OAS 3.0 ({@code type}) and OAS 3.1
     * ({@code types}). If multiple types are declared the first non-"null" type wins.
     */
    public static String getEffectiveType(Schema schema) {
        if (schema.getType() != null)
            return schema.getType();
        var types = schema.getTypes();
        if (types == null)
            return null;
        for (Object type : types) {
            if (!"null".equals(type))
                return type.toString();
        }
        return null;
    }

    /** True if the schema's type is {@code string}, whether declared via OAS 3.0 {@code type} or OAS 3.1 {@code types}. */
    public static boolean isString(Schema schema) {
        if ("string".equals(schema.getType()))
            return true;
        return schema.getTypes() != null && schema.getTypes().contains("string");
    }

    /** A string schema with {@code format: binary} or {@code byte}, i.e. opaque binary content. */
    public static boolean isBinaryString(Schema schema) {
        if (!isString(schema))
            return false;
        String format = schema.getFormat();
        return "binary".equals(format) || "byte".equals(format);
    }

    /** True if the schema's effective type is {@code array}. */
    public static boolean isArray(Schema schema) {
        return "array".equals(getEffectiveType(schema));
    }

    /** True for the primitive scalar types string, integer, number and boolean. */
    public static boolean isScalar(Schema schema) {
        String type = getEffectiveType(schema);
        return "string".equals(type) || "integer".equals(type) || "number".equals(type) || "boolean".equals(type);
    }

    /**
     * True if the schema represents structured content, i.e. its effective type is {@code object} or
     * {@code array}, or it declares {@code properties}. Used to decide whether a part without an explicit
     * content type defaults to JSON rather than {@code text/plain}.
     * <p>
     * A {@code $ref} is resolved against {@code api} first (see {@link #resolveRef}), so the referenced
     * schema's type is what gets inspected. A ref that cannot be resolved is reported as not structured.
     */
    public static boolean isObjectOrArray(OpenAPI api, Schema schema) {
        schema = resolveRef(api, schema);
        if (schema == null)
            return false;
        var type = getEffectiveType(schema);
        if ("object".equals(type) || "array".equals(type))
            return true;
        return schema.getProperties() != null;
    }

    /**
     * Parses raw text into a typed JSON value according to the schema's scalar type. If the text does
     * not match the declared numeric/boolean type it is kept as text so the validator can report the
     * type mismatch.
     */
    public static JsonNode parseScalar(String text, Schema schema) {
        JsonNodeFactory nf = JsonNodeFactory.instance;
        String type = getEffectiveType(schema);
        if (type == null)
            return nf.textNode(text);
        return switch (type) {
            case "integer" -> {
                try { yield nf.numberNode(Long.parseLong(text.trim())); }
                catch (NumberFormatException e) { yield nf.textNode(text); }
            }
            case "number" -> {
                try { yield nf.numberNode(Double.parseDouble(text.trim())); }
                catch (NumberFormatException e) { yield nf.textNode(text); }
            }
            case "boolean" -> switch (text.trim()) {
                case "true" -> nf.booleanNode(true);
                case "false" -> nf.booleanNode(false);
                default -> nf.textNode(text);
            };
            default -> nf.textNode(text);
        };
    }
}
