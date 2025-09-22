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

public class SchemaValidator implements JsonSchemaValidator {

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
    public String canValidate(Object obj) {
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
            schema = SchemaUtil.getSchemaFromRef(api, schema.get$ref());
            if (schema == null)
                throw new RuntimeException("Should not happen!");
        }

        if ((value == null || value instanceof NullNode) && isNullable())
            return errors;

        errors.add(new StringRestrictionValidator(schema).validate(ctx, value));
        errors.add(new NumberRestrictionValidator(schema).validate(ctx, value));
        errors.add(validateByType(ctx, value));
        return errors;
    }

    private boolean isNullable() {
        return (schema.getNullable() != null && schema.getNullable()) || (schema.getTypes() != null && schema.getTypes().contains("null")) ||
               (schema.getType() != null && schema.getType().equals("null"));
    }

    private ValidationErrors validateByType(ValidationContext ctx, Object value) {

        String type = schema.getType();

        if (schemaHasNoTypeAndTypes(type)) {
            return validateMultipleTypes(List.of("string", "number", "integer", "boolean", "array", "object", "null"), ctx, value);
        }

        // type in schema has only one type
        if (type != null)
            return validateSingleType(ctx, value, type);

        // At that point: schema.types is used
        return validateMultipleTypes(new ArrayList<>(schema.getTypes()), ctx, value);
    }

    private @Nullable ValidationErrors validateMultipleTypes(List<String> types, ValidationContext ctx, Object value) {
        String typeOfValue = getTypeOfValue(types, value);

        ValidationErrors errors = getTypeNotMatchError(types, ctx, value, typeOfValue);
        if (errors != null) return errors;

        return validateSingleType(ctx, value, typeOfValue);

    }

    /**
     * <p>If the type of the value does not match any of the specified types, an error is created.
     * For example, if the types are ["number", "null"] and the value is "Manila" (a string),
     * a validation error is returned.
     *
     * @param types the list of allowed types in the schema, e.g., ["number", "null"]
     * @param ctx the validation context
     * @param value the value being validated
     * @param typeOfValue the determined type of the value
     * @return a ValidationErrors object if there is a type mismatch, or null if the type is valid
     */
    private @Nullable ValidationErrors getTypeNotMatchError(List<String> types, ValidationContext ctx, Object value, String typeOfValue) {
        if (typeOfValue == null || !types.contains(typeOfValue)) {
            return ValidationErrors.create(ctx,"%s is of type %s which does not match any of %s".formatted( value, typeOfValue, types));
        }
        return null;
    }

    /**
     * Determines the type of a value based on declared schema types.
     *
     * <p>Returns "number" if the value is an "integer" and "number" is allowed in the types,
     * otherwise returns the value's original type.
     *
     * @param types Declared types in a schema like types: [integer,string,null]
     * @param value value from the document that has to be validated
     * @return name of the type that applies
     */
    private static @Nullable String getTypeOfValue(List<String> types, Object value) {
        String typeOfValue = getType(value);
        if (Objects.equals(typeOfValue, INTEGER) && !types.contains(typeOfValue) && types.contains(NUMBER))
            return NUMBER;
        return typeOfValue;
    }

    private static String getType(Object obj) {
        return getValidatorClasses().stream()
                .map(validator -> validator.canValidate(obj))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }


    private ValidationErrors validateSingleType(ValidationContext ctx, Object value, String type) {
        try {
            return switch (type) {
                case NULL -> new NullValidator().validate(ctx, value);
                case NUMBER -> new NumberValidator().validate(ctx, value);
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

    private static List<JsonSchemaValidator> getValidatorClasses() {
        return List.of(
                new NullValidator(),
                new IntegerValidator(),
                new NumberValidator(),
                new StringValidator(null),
                new BooleanValidator(),
                new ArrayValidator(null, null),
                new ObjectValidator(null, null)
        );
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