/*
 *  Copyright 2026 predic8 GmbH, www.predic8.com
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

package com.predic8.membrane.core.openapi.validators;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.predic8.membrane.core.openapi.model.Message;
import com.predic8.membrane.core.openapi.util.SchemaUtil;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;

import java.io.IOException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.predic8.membrane.core.openapi.validators.ValidationContext.ValidatedEntityType.BODY;
import static java.lang.Boolean.FALSE;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Validates an {@code application/x-www-form-urlencoded} message body against an OpenAPI media type.
 * <p>
 * The body is parsed into form fields (percent-decoded, repeated keys collected into a list) and each
 * field is matched against a property of the (object) schema:
 * <ul>
 *   <li>scalar properties are parsed into their declared type and validated (incl. {@code format})</li>
 *   <li>object/array properties are expected as a stringified JSON value (RFC 1866), parsed and validated</li>
 *   <li>array properties take all occurrences of the field name, each validated against the items schema</li>
 * </ul>
 */
@SuppressWarnings("rawtypes")
public class FormUrlEncodedValidator {

    private static final ObjectMapper om = new ObjectMapper();

    private final OpenAPI api;

    public FormUrlEncodedValidator(OpenAPI api) {
        this.api = api;
    }

    public ValidationErrors validate(ValidationContext ctx, MediaType mediaType, Message<?, ?> message) {
        ctx = ctx.entityType(BODY);
        var errors = new ValidationErrors();

        // The media type schema may be a $ref to a component; resolve it so its properties and
        // required list are visible.
        Schema schema = SchemaUtil.resolveRef(api, mediaType.getSchema());
        if (schema == null) {
            // Without a schema there is nothing to validate the fields against.
            return errors;
        }

        String raw;
        try {
            raw = message.getBody().asString();
        } catch (IOException e) {
            return errors.add(ctx, "The application/x-www-form-urlencoded body cannot be read: " + e.getMessage());
        }

        var form = parse(raw);

        errors.add(validateRequired(ctx, schema, form));
        errors.add(validateFields(ctx, schema, form));
        return errors;
    }

    private Map<String, List<String>> parse(String raw) {
        Map<String, List<String>> form = new LinkedHashMap<>();
        if (raw == null || raw.isBlank())
            return form;
        for (var pair : raw.split("&")) {
            if (pair.isEmpty())
                continue;
            int eq = pair.indexOf('=');
            form.computeIfAbsent(getKey(pair, eq), k -> new ArrayList<>()).add(getValue(pair, eq));
        }
        return form;
    }

    private static String getValue(String pair, int eq) {
        return URLDecoder.decode(eq >= 0 ? pair.substring(eq + 1) : "", UTF_8);
    }

    private static String getKey(String pair, int eq) {
        return URLDecoder.decode(eq >= 0 ? pair.substring(0, eq) : pair, UTF_8);
    }

    private ValidationErrors validateRequired(ValidationContext ctx, Schema schema, Map<String, List<String>> form) {
        var errors = new ValidationErrors();
        if (schema.getRequired() == null)
            return errors;
        for (Object required : schema.getRequired()) {
            if (!form.containsKey(required.toString()))
                errors.add(ctx, "Required property '%s' is missing in the application/x-www-form-urlencoded body.".formatted(required));
        }
        return errors;
    }

    private ValidationErrors validateFields(ValidationContext ctx, Schema schema, Map<String, List<String>> form) {
        var errors = new ValidationErrors();
        Map<String, Schema> properties = schema.getProperties();

        for (var entry : form.entrySet()) {
            var name = entry.getKey();
            var values = entry.getValue();

            Schema propertySchema = properties != null ? properties.get(name) : null;
            if (propertySchema == null) {
                if (FALSE.equals(schema.getAdditionalProperties()))
                    errors.add(ctx, "The application/x-www-form-urlencoded body contains an unexpected property '%s'.".formatted(name));
                continue;
            }

            propertySchema = SchemaUtil.resolveRef(api, propertySchema);
            var fieldCtx = ctx.addJSONpointerSegment(name);

            if (SchemaUtil.isArray(propertySchema)) {
                errors.add(new SchemaValidator(api, propertySchema).validate(fieldCtx, toArrayNode(values, propertySchema)));
            } else {
                errors.add(new SchemaValidator(api, propertySchema).validate(fieldCtx, toNode(values.getFirst(), propertySchema)));
            }
        }
        return errors;
    }

    private ArrayNode toArrayNode(List<String> values, Schema arraySchema) {
        Schema items = SchemaUtil.resolveRef(api, arraySchema.getItems());
        ArrayNode arrayNode = JsonNodeFactory.instance.arrayNode();
        for (String value : values)
            arrayNode.add(items != null ? toNode(value, items) : JsonNodeFactory.instance.textNode(value));
        return arrayNode;
    }

    /**
     * Turns a raw form value into a JSON node according to the schema: object/array values are expected
     * as stringified JSON, everything else is parsed as a scalar. Unparseable JSON is kept as text so
     * the schema validator reports the type mismatch.
     */
    private JsonNode toNode(String value, Schema schema) {
        if (SchemaUtil.isObjectOrArray(schema)) {
            try {
                return om.readTree(value);
            } catch (Exception e) {
                return JsonNodeFactory.instance.textNode(value);
            }
        }
        return SchemaUtil.parseScalar(value, schema);
    }
}
