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
import com.predic8.membrane.core.openapi.OpenAPIParsingException;
import com.predic8.membrane.core.openapi.model.Message;
import com.predic8.membrane.core.openapi.util.SchemaUtil;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URLDecoder;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static com.predic8.membrane.core.openapi.util.SchemaUtil.isObjectOrArray;
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

    private static final Logger log = LoggerFactory.getLogger(FormUrlEncodedValidator.class);
    private static final ObjectMapper om = new ObjectMapper();

    private final OpenAPI api;

    public FormUrlEncodedValidator(OpenAPI api) {
        this.api = api;
    }

    public ValidationErrors validate(ValidationContext ctx, MediaType mediaType, Message<?, ?> message) {
        String raw;
        try {
            raw = message.getBody().asString();
        } catch (IOException e) {
            return ValidationErrors.error(ctx.entityType(BODY), "The application/x-www-form-urlencoded body cannot be read: " + e.getMessage());
        }
        return validate(ctx.entityType(BODY), mediaType, raw);
    }

    /**
     * Validates a raw {@code application/x-www-form-urlencoded} string (a message body or an
     * OpenAPI 3.2 {@code in: querystring} value) against the media type's schema.
     */
    public ValidationErrors validate(ValidationContext ctx, MediaType mediaType, String raw) {
        var errors = new ValidationErrors();

        // The media type schema may be a $ref to a component; resolve it so its properties and
        // required list are visible.
        Schema schema = SchemaUtil.resolveRef(api, mediaType.getSchema());
        if (schema == null) {
            // Without a schema there is nothing to validate the fields against.
            return errors;
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
        Map<String, Schema> patternProperties = schema.getPatternProperties();
        // Compile once per validation; regex order makes pattern-only decoding independent of map order.
        var patterns = new LinkedHashMap<Pattern, Schema>();
        if (patternProperties != null)
            patternProperties.entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> {
                        try {
                            patterns.put(Pattern.compile(entry.getKey()), entry.getValue());
                        } catch (PatternSyntaxException e) {
                            // Invalid schema configuration must not become a client validation error
                            // or silently drop the constraints associated with this pattern.
                            var message = "Invalid patternProperties regex '%s': %s (index %d)."
                                    .formatted(entry.getKey(), e.getDescription(), e.getIndex());
                            log.error(message);
                            var error = new OpenAPIParsingException(message);
                            error.initCause(e);
                            throw error;
                        }
                    });

        for (var entry : form.entrySet()) {
            var name = entry.getKey();
            var values = entry.getValue();
            // A declared property and all matching patterns apply together.
            var fieldSchemas = new ArrayList<Schema>();
            Schema propertySchema = properties != null ? properties.get(name) : null;
            if (propertySchema != null)
                fieldSchemas.add(propertySchema);
            patterns.forEach((pattern, patternSchema) -> {
                // JSON Schema patterns may match anywhere in the field name, not just the whole name.
                if (pattern.matcher(name).find())
                    fieldSchemas.add(patternSchema);
            });
            // A field is additional only if neither properties nor patternProperties covers it.
            if (fieldSchemas.isEmpty()) {
                var additional = SchemaUtil.getAdditionalProperties(schema);
                if (FALSE.equals(additional))
                    errors.add(ctx, "The application/x-www-form-urlencoded body contains an unexpected property '%s'.".formatted(name));
                if (!(additional instanceof Schema<?> additionalSchema))
                    continue;
                fieldSchemas.add(additionalSchema);
            }

            // Resolve references for decoding, but keep the original schemas for validation,
            // including any constraints next to a $ref.
            var decodingSchemas = fieldSchemas.stream().map(s -> {
                var resolved = SchemaUtil.resolveRef(api, s);
                if (resolved == null)
                    throw new OpenAPIParsingException("Cannot resolve schema reference '%s' for form field '%s'."
                            .formatted(s.get$ref(), name));
                return decodingSchema(s, resolved);
            }).toList();
            var decodingSchema = decodingSchema(decodingSchemas);
            // Decode once: converting separately could let the same field satisfy incompatible
            // types (e.g. integer 1 for one schema and string "1" for another).
            var node = SchemaUtil.isArray(decodingSchema)
                    ? toArrayNode(values, decodingSchemas)
                    : toNode(values.getFirst(), decodingSchema);
            var fieldCtx = ctx.addJSONpointerSegment(name);
            for (var fieldSchema : fieldSchemas)
                errors.add(new SchemaValidator(api, fieldSchema).validate(fieldCtx, node));
        }
        return errors;
    }

    /**
     * Choose a type accepted by every applicable schema, treating integers as numbers and
     * typeless schemas as unconstrained. Preserve declaration order among compatible types.
     * If types conflict, retain a declared type so validation of the shared node reports it.
     */
    private Schema decodingSchema(List<Schema> schemas) {
        var declaredTypes = schemas.stream().map(this::decodingTypes).filter(types -> !types.isEmpty()).toList();
        if (!declaredTypes.isEmpty()) {
            var compatibleTypes = new LinkedHashSet<>(declaredTypes.getFirst());
            declaredTypes.forEach(compatibleTypes::retainAll);
            // Form scalar decoding does not turn text into a JSON null.
            compatibleTypes.remove("null");
            if (!compatibleTypes.isEmpty())
                return new Schema<>().type(compatibleTypes.getFirst());
        }
        return schemas.stream()
                .filter(s -> SchemaUtil.getEffectiveType(s) != null)
                .findFirst().orElse(schemas.isEmpty() ? new Schema<>() : schemas.getFirst());
    }

    /**
     * Keep a sibling type for decoding when the referenced component does not declare one.
     * The component is shared by the OpenAPI model, so do not add the sibling type to it.
     */
    private Schema decodingSchema(Schema original, Schema resolved) {
        if (SchemaUtil.getEffectiveType(resolved) != null || SchemaUtil.getEffectiveType(original) == null)
            return resolved;

        var decoding = new Schema<>();
        decoding.setType(original.getType());
        if (original.getTypes() != null)
            decoding.setTypes(new LinkedHashSet<>(original.getTypes()));
        decoding.setItems(resolved.getItems());
            decoding.setPrefixItems(resolved.getPrefixItems());
        return decoding;
    }

    private Set<String> decodingTypes(Schema schema) {
        var types = new LinkedHashSet<String>();
        if (schema.getType() != null)
            types.add(schema.getType());
        else if (schema.getTypes() != null)
            types.addAll(schema.getTypes());
        if (types.contains("number"))
            types.add("integer");
        return types;
    }

    private ArrayNode toArrayNode(List<String> values, List<Schema> schemas) {
        ArrayNode arrayNode = JsonNodeFactory.instance.arrayNode();
        for (int i = 0; i < values.size(); i++) {
            var itemSchemas = new ArrayList<Schema>();
            for (var schema : schemas) {
                var items = SchemaUtil.resolveRef(api, SchemaUtil.itemSchema(schema, i));
                if (items != null)
                    itemSchemas.add(items);
            }
            arrayNode.add(toNode(values.get(i), decodingSchema(itemSchemas)));
        }
        return arrayNode;
    }

    /**
     * Turns a raw form value into a JSON node according to the schema: object/array values are expected
     * as stringified JSON, everything else is parsed as a scalar. Unparseable JSON is kept as text so
     * the schema validator reports the type mismatch.
     */
    private JsonNode toNode(String value, Schema schema) {
        if (isObjectOrArray(api, schema)) {
            try {
                return om.readTree(value);
            } catch (Exception e) {
                return JsonNodeFactory.instance.textNode(value);
            }
        }
        return SchemaUtil.parseScalar(value, schema);
    }
}
