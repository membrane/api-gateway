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

package com.predic8.membrane.core.openapi.validators;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.predic8.membrane.core.openapi.util.*;
import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.media.*;
import org.slf4j.*;

import java.io.*;
import java.util.*;
import java.util.regex.*;

import static com.predic8.membrane.core.openapi.util.Utils.*;
import static java.lang.String.*;

/**
 * Not supported:
 * <p>
 * - propertyNames from JSON Schema draft 6
 * - is it part of OpenAPI?
 */

public class ObjectValidator implements JsonSchemaValidator {

    private static final Logger log = LoggerFactory.getLogger(ObjectValidator.class.getName());

    @SuppressWarnings("rawtypes")
    private final Schema schema;

    private final OpenAPI api;

    @SuppressWarnings("rawtypes")
    public ObjectValidator(OpenAPI api, Schema schema) {
        this.api = api;
        this.schema = schema;
    }

    public String canValidate(Object obj) {
        if (obj instanceof JsonNode j) {
            return j instanceof ObjectNode ? OBJECT : null;
        } else if (obj instanceof InputStream) {
            throw new RuntimeException("InputStream should not happen!");
        } else {
            log.warn("This should not happen. Please check.");
            throw new RuntimeException("Value cannot be read as object.");
        }
    }

    @Override
    public ValidationErrors validate(ValidationContext ctx, Object obj) {
        ctx = ctx.schemaType("object");

        if (canValidate(obj) == null || !(obj instanceof ObjectNode node)) {
            return ValidationErrors.create(ctx.statusCode(400), format("Value %s is not an object.", obj));
        }

        ValidationErrors errors = validateRequiredProperties(ctx, node);
        errors.add(validateAdditionalProperties(ctx, node));
        errors.add(validateProperties(ctx, node));
        errors.add(validatePatternProperties(ctx, node));
        errors.add(validateSize(ctx, node));
        errors.add(validateDiscriminator(ctx, node));
        return errors;
    }

    private ValidationErrors validateDiscriminator(ValidationContext ctx, JsonNode node) {
        if (schema.getDiscriminator() == null)
            return null;

        String propertyName = schema.getDiscriminator().getPropertyName();

        String propertyValue = getBaseSchemaName(node, propertyName);
        if (propertyValue == null) {
            return ValidationErrors.create(ctx.statusCode(400), format("Discriminator property '%s' is not set.", propertyName));
        }
        if (isMapped(propertyValue)) {
            propertyValue = schema.getDiscriminator().getMapping().get(propertyValue);
            if (propertyValue != null && propertyValue.startsWith("#"))
                return validateRef(ctx, node, propertyValue);
        }

        Schema<?> schema = getBaseSchema(propertyValue);
        if (schema == null) {
            return ValidationErrors.create(ctx.statusCode(400), format("Discriminator value %s is not an valid type.", propertyValue));
        }
        return new SchemaValidator(api, schema).validate(ctx, node);
    }

    private ValidationErrors validateRef(ValidationContext ctx, JsonNode node, String propertyValue) {
        var schema2 = SchemaUtil.getSchemaFromRef(api, propertyValue);
        if (schema2 == null)
            throw new RuntimeException("Should not happen!");
        return new SchemaValidator(api, schema2).validate(ctx, node);
    }

    private boolean isMapped(String discriminatorValue) {
        return schema.getDiscriminator().getMapping() != null && schema.getDiscriminator().getMapping().containsKey(discriminatorValue);
    }

    @SuppressWarnings("rawtypes")
    private Schema getBaseSchema(String propertyValue) {
        return api.getComponents().getSchemas().get(propertyValue);
    }

    private String getBaseSchemaName(JsonNode node, String propertyName) {
        JsonNode v = node.get(propertyName);
        return (v == null || v.isNull()) ? null : v.asText();
    }

    private ValidationErrors validateSize(ValidationContext ctx, JsonNode node) {
        ValidationErrors errors = new ValidationErrors();
        errors.add(validateMinProperties(ctx, node));
        errors.add(validateMaxProperties(ctx, node));
        return errors;
    }

    private ValidationErrors validateMinProperties(ValidationContext ctx, JsonNode node) {
        if (schema.getMinProperties() != null && node.size() < schema.getMinProperties()) {
            return ValidationErrors.create(ctx, String.format("Object has %d properties. This is smaller than minProperties of %d.", node.size(), schema.getMinProperties()));
        }
        return null;
    }

    private ValidationErrors validateMaxProperties(ValidationContext ctx, JsonNode node) {
        if (schema.getMaxProperties() != null && node.size() > schema.getMaxProperties()) {
            return ValidationErrors.create(ctx, String.format("Object has %d properties. This is more than maxProperties of %d.", node.size(), schema.getMaxProperties()));
        }
        return null;
    }

    @SuppressWarnings("rawtypes")
    private ValidationErrors validateAdditionalProperties(ValidationContext ctx, JsonNode node) {
        if (schema.getAdditionalProperties() == null)
            return null;

        Map<String, JsonNode> additionalProperties = getAddionalProperties(node);

        if (additionalProperties.isEmpty())
            return null;

        ValidationErrors errors = new ValidationErrors();

        if (schema.getAdditionalProperties() instanceof Schema) {
            additionalProperties.forEach((propName, value) ->
                    errors.add(new SchemaValidator(api, (Schema) schema.getAdditionalProperties()).validate(ctx.addJSONpointerSegment(propName), value)));
            return errors;
        }
        if (schema.getAdditionalProperties() instanceof Boolean b) {
            if (b) {
                return null; // allowed
            }
            return errors.add(ctx.statusCode(400),
                    format("The object has the additional %s: %s. But the schema does not allow additional properties.",
                            getPropertyOrIes(additionalProperties.keySet()),
                            joinByComma(additionalProperties.keySet())));
        }
        // Unknown type – be safe and return an error
        return errors.add(ctx.statusCode(400),
                format("Unsupported additionalProperties: %s", joinByComma(additionalProperties.keySet())));
    }

    private String getPropertyOrIes(Set<String> addionalProperties) {
        String propWord = "Property";
        if (addionalProperties.size() > 1) {
            propWord = "Properties";
        }
        return propWord;
    }

    private Map<String, JsonNode> getAddionalProperties(JsonNode node) {
        Map<String, JsonNode> addionalProperties = new HashMap<>();
        for (Iterator<String> it = node.fieldNames(); it.hasNext(); ) {
            String propName = it.next();
            if (schema.getProperties() == null || !schema.getProperties().containsKey(propName)) {
                addionalProperties.put(propName, node.get(propName));
            }
        }
        return addionalProperties;
    }

    private ValidationErrors validateRequiredProperties(ValidationContext ctx, JsonNode node) {

        @SuppressWarnings("unchecked") List<String> required = schema.getRequired();
        if (required == null)
            return new ValidationErrors();

        return createErrorsForMissingRequiredProperties(ctx, getMissingProperties(ctx, node, required));
    }

    private List<String> getMissingProperties(ValidationContext ctx, JsonNode node, List<String> required) {
        List<String> missingProperties = new ArrayList<>();
        required.forEach(requiredProp -> {
            if (node.get(requiredProp) == null) {
                if (ctx.getValidatedEntity().equals("REQUEST")) {
                    if (isPropertyReadOnly(requiredProp)) {
                        return;
                    }
                } else {
                    if (isPropertyWriteOnly(requiredProp)) {
                        return;
                    }
                }
                missingProperties.add(requiredProp);
            }
        });
        return missingProperties;
    }

    @SuppressWarnings("rawtypes")
    private boolean isPropertyReadOnly(String propertyName) {
        Schema propSchema = (Schema) schema.getProperties().get(propertyName);
        return propSchema.getReadOnly() != null && propSchema.getReadOnly();
    }

    @SuppressWarnings("rawtypes")
    private boolean isPropertyWriteOnly(String propertyName) {
        Schema propSchema = (Schema) schema.getProperties().get(propertyName);
        return propSchema.getWriteOnly() != null && propSchema.getWriteOnly();
    }

    private ValidationErrors createErrorsForMissingRequiredProperties(ValidationContext ctx, List<String> missingProperties) {
        ValidationErrors errors = new ValidationErrors();

        if (missingProperties.size() == 1) {
            errors.add(new ValidationError(ctx.addJSONpointerSegment(missingProperties.getFirst()), format("Required property %s is missing.", missingProperties.getFirst())));
        } else if (missingProperties.size() > 1) {
            String missing = String.join(",", missingProperties);
            errors.add(new ValidationError(ctx, format("Required properties %s are missing in object %s.", missing, ctx.getJSONpointer())));
        }
        return errors;
    }

    private ValidationErrors validateProperties(ValidationContext ctx, JsonNode node) {

        if (schema.getProperties() == null)
            return null;

        ValidationErrors errors = new ValidationErrors();

        getPropertiesFromSchema().forEach((propertyName, propertySchema) -> {
            errors.add(validateProperty(propertyName, propertySchema, node, ctx.addJSONpointerSegment(propertyName)));
            errors.add(validateReadOnlyProperty(ctx, node, propertyName, propertySchema));
            errors.add(validateWriteOnlyProperty(ctx, node, propertyName, propertySchema));
        });
        return errors;
    }

    private ValidationErrors validatePatternProperties(ValidationContext ctx, JsonNode node) {
        if (schema.getPatternProperties() == null) {
            return null;
        }
        ValidationErrors errors = new ValidationErrors();

        getPatternPropertiesFromSchema().forEach((regex, propSchema) -> {
            Pattern pattern = Pattern.compile(regex);

            for (Iterator<String> it = node.fieldNames(); it.hasNext(); ) {
                String fieldName = it.next();
                if (pattern.matcher(fieldName).matches()) {
                    JsonNode childNode = node.get(fieldName);
                    errors.add(new SchemaValidator(api, propSchema)
                            .validate(ctx.addJSONpointerSegment(fieldName), childNode));
                }
            }
        });
        return errors;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Map<String, Schema> getPropertiesFromSchema() {
        return (Map<String, Schema>) schema.getProperties();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Map<String, Schema> getPatternPropertiesFromSchema() {
        return (Map<String, Schema>) schema.getPatternProperties();
    }

    @SuppressWarnings("rawtypes")
    private ValidationErrors validateReadOnlyProperty(ValidationContext ctx, JsonNode node, String propertyName, Schema propertySchema) {
        if (propertySchema.getReadOnly() == null || !propertySchema.getReadOnly())
            return null;

        if (!ctx.getValidatedEntity().equals("REQUEST"))
            return null;

        if (node.get(propertyName) == null)
            return null;

        return ValidationErrors.create(ctx.addJSONpointerSegment(propertyName), String.format("The property %s is read only. But the request contains the value %s for this field.", propertyName, node.get(propertyName)));
    }

    @SuppressWarnings("rawtypes")
    private ValidationErrors validateWriteOnlyProperty(ValidationContext ctx, JsonNode node, String
            propertyName, Schema propertySchema) {
        if (propertySchema.getWriteOnly() == null || !propertySchema.getWriteOnly())
            return null;

        if (!ctx.getValidatedEntity().equals("RESPONSE"))
            return null;

        if (node.get(propertyName) == null)
            return null;

        return ValidationErrors.create(ctx.addJSONpointerSegment(propertyName), String.format("The property %s is write only. But the response contained the value %s.", propertyName, node.get(propertyName)));
    }

    @SuppressWarnings("rawtypes")
    private ValidationErrors validateProperty(String propertyName, Schema schema, JsonNode node, ValidationContext
            ctx) {
        ValidationErrors errors = new ValidationErrors();
        if (node.get(propertyName) != null)
            errors.add(new SchemaValidator(api, schema).validate(ctx, node.get(propertyName)));
        return errors;
    }
}
