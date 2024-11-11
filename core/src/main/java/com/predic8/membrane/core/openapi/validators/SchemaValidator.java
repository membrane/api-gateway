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

import com.fasterxml.jackson.databind.node.NullNode;
import com.predic8.membrane.core.openapi.OpenAPIParsingException;
import com.predic8.membrane.core.openapi.model.Body;
import com.predic8.membrane.core.openapi.util.SchemaUtil;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.predic8.membrane.core.openapi.util.SchemaUtil.getSchemaNameFromRef;
import static com.predic8.membrane.core.openapi.validators.ValidationContext.ValidatedEntityType.BODY;

public class SchemaValidator implements IJSONSchemaValidator {

    private static final Logger log = LoggerFactory.getLogger(SchemaValidator.class.getName());

    @SuppressWarnings("rawtypes")
    private Schema schema;
    final private OpenAPI api;

    @SuppressWarnings("rawtypes")
    public SchemaValidator(OpenAPI api, Schema schema) {
        if (schema == null)
            throw new OpenAPIParsingException("Could not parse OpenAPI");

        this.schema = schema;
        this.api = api;
    }

    // Not needed in SchemaValidator, but necessary for interface.
    public String isOfType(Object obj) {
        return null;
    }

    @Override
    public ValidationErrors validate(ValidationContext ctx, Object obj) {

        ValidationErrors errors = new ValidationErrors();

        if (obj == null)
            return errors.add(ctx, "Got null to validate!");

        Object value;
        try {
            value = resolveValueAndParseJSON(obj);
        } catch (IOException e) {
            log.warn("Cannot parse body. " + e);
            return errors.add(new ValidationError(ctx.statusCode(400).entityType(BODY).entity("REQUEST"), "Request body cannot be parsed as JSON"));
        }

        if (schema.getAllOf() != null)
            errors.add(new AllOfValidator(api, schema).validate(ctx, obj));

        if (schema.getAnyOf() != null)
            errors.add(new AnyOfValidator(api, schema).validate(ctx, obj));

        if (schema.getOneOf() != null)
            errors.add(new OneOfValidator(api, schema).validate(ctx, obj));

        if (schema.getNot() != null)
            errors.add(new NotValidator(api, schema).validate(ctx, obj));

        if (schema.get$ref() != null && !getSchemaNameFromRef(schema).equals(ctx.getComplexType())) {
            ctx = ctx.complexType(getSchemaNameFromRef(schema));
            schema = SchemaUtil.getSchemaFromRef(api, schema);
            if (schema == null)
                throw new RuntimeException("Should not happen!");
        }

        if (schemaHasNoTypeAndTypes(schema.getType())) {
            if ((value == null || value instanceof NullNode) && isNullable())
                return ValidationErrors.create(ctx, "Value is null and no type is set.");
        } else {
            if ((value == null || value instanceof NullNode) && isNullable())
                return errors;
        }

        errors.add(new StringRestrictionValidator(schema).validate(ctx, value));
        errors.add(new NumberRestrictionValidator(schema).validate(ctx, value));
        errors.add(validateByType(ctx, value));
        return errors;
    }

    private boolean isNullable() {
        return (schema.getNullable() != null && schema.getNullable()) || schema.getTypes().contains("null");
    }

    private ValidationErrors validateByType(ValidationContext ctx, Object value) {

        String type = schema.getType();

        if (schemaHasNoTypeAndTypes(type)) {
            return null;
        }

        // type in schema has only one type
        if (type != null)
            return validateSingleType(ctx, value, type);

        // At that point: schema.types is used
        return getValidationErrors(new ArrayList<>(schema.getTypes()), ctx, value);
    }

    private @Nullable ValidationErrors getValidationErrors(List<String> types, ValidationContext ctx, Object value) {
        String t = getType(value);

        if (Objects.equals(t, "integer") && !types.contains(t) && types.contains("number")) t = "number";

        if (t == null || !types.contains(t)) {
            ValidationErrors allErrors = new ValidationErrors();
            for(String tp : types) {
                allErrors.add(validateSingleType(ctx, value, tp));
            }
            ValidationErrors errors = new ValidationErrors();
            errors.add(new ValidationError("%s does not match one of these types: %s. Details: %s".formatted(value, types, allErrors.toString())));
            return errors;
        }
        return validateSingleType(ctx, value, t);

    }

    private String getType(Object obj) {
        return getValidatorClasses().stream()
                .map(this::createValidatorInstance)
                .map(validator -> validator.isOfType(obj))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private IJSONSchemaValidator createValidatorInstance(Class<? extends IJSONSchemaValidator> clazz) {
        try {
            return clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create validator instance", e);
        }
    }


    private ValidationErrors validateSingleType(ValidationContext ctx, Object value, String type) {
        try {
            return switch (type) {
                case "number" -> new NumberValidator().validate(ctx, value);
                case "integer" -> new IntegerValidator().validate(ctx, value);
                case "string" -> new StringValidator(schema).validate(ctx, value);
                case "boolean" -> new BooleanValidator().validate(ctx, value);
                case "array" -> new ArrayValidator(api, schema).validate(ctx, value);
                case "object" -> new ObjectValidator(api, schema).validate(ctx, value);
                default -> throw new RuntimeException("Should not happen! " + type);
            };
        } catch (Exception e) {
            return ValidationErrors.create(ctx, "%s is not of %s format.".formatted(value, type));
        }
    }

    private boolean schemaHasNoTypeAndTypes(String type) {
        return type == null && (schema.getTypes() == null || schema.getTypes().isEmpty());
    }

    private List<Class<? extends IJSONSchemaValidator>> getValidatorClasses() {
        // Order must be kept intact as: IntegerValidator NumberValidator StringValidator BooleanValidator ArrayValidator ObjectValidator
        return List.of(IntegerValidator.class, NumberValidator.class, StringValidator.class, BooleanValidator.class, ArrayValidator.class, ObjectValidator.class);
    }

    /**
     * Unwrap or read value in case of InputStream or Body objects
     */
    private Object resolveValueAndParseJSON(Object obj) throws IOException {
        if (obj instanceof Body)
            return ((Body) obj).getJson();

        // Just temp to make sure there is no inputstream anymore! Can be deleted later!
        if (obj instanceof InputStream) {
            throw new RuntimeException("InputStream!");
        }

        return obj;
    }
}