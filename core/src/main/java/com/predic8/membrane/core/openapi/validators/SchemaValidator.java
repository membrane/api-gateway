package com.predic8.membrane.core.openapi.validators;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.predic8.membrane.core.openapi.model.*;
import com.predic8.membrane.core.openapi.util.*;

import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.media.*;
import org.slf4j.*;

import java.io.*;

import static com.predic8.membrane.core.openapi.util.SchemaUtil.getSchemaNameFromRef;
import static com.predic8.membrane.core.openapi.validators.ValidationContext.ValidatedEntityType.BODY;

public class SchemaValidator implements IJSONSchemaValidator {

    private static Logger log = LoggerFactory.getLogger(SchemaValidator.class.getName());

    final private ObjectMapper om = new ObjectMapper();

    @SuppressWarnings("rawtypes")
    private Schema schema;
    final private OpenAPI api;

    @SuppressWarnings("rawtypes")
    public SchemaValidator(OpenAPI api, Schema schema) {
        if (schema == null)
            throw new RuntimeException("Should not happen!");

        this.schema = schema;
        this.api = api;
    }

    @Override
    public ValidationErrors validate(ValidationContext ctx, Object obj) {

        ValidationErrors errors = new ValidationErrors();

        if (obj == null) {
            return errors.add(ctx, "Got null to validate!");
        }

        Object value ;
        try {
            value = resolveValueAndParseJSON(obj);
        } catch (IOException e) {
            log.warn("Cannot parse body. " + e);
            return errors.add(new ValidationError(ctx.statusCode(400).validatedEntityType(BODY).validatedEntity("REQUEST"), "Request body cannot be parsed as JSON"));
        }

        if (schema.getAllOf() != null) {
            errors.add(new AllOfValidator(api, schema).validate(ctx, obj));
        }
        if (schema.getAnyOf() != null) {
            errors.add(new AnyOfValidator(api, schema).validate(ctx, obj));
        }
        if (schema.getOneOf() != null) {
            errors.add(new OneOfValidator(api, schema).validate(ctx, obj));
        }
        if (schema.getNot() != null) {
            errors.add(new NotValidator(api, schema).validate(ctx, obj));
        }

        if (schema.get$ref() != null) {
            if (!getSchemaNameFromRef(schema).equals(ctx.getComplexType())) {
                ctx = ctx.complexType(getSchemaNameFromRef(schema));
                schema = SchemaUtil.getSchemaFromRef(api, schema);
                if (schema == null)
                    throw new RuntimeException("Should not happen!");
            }
        }

        if (schema.getType() == null) {
            if ((value == null || value instanceof  NullNode) && schema.getNullable()) {
                return ValidationErrors.create(ctx,"Value is null and no type is set.");
            }
        } else {
            if ((value == null || value instanceof  NullNode) && schema.getNullable()) {
                return errors;
            }
        }

        errors.add(new StringRestrictionValidator(schema).validate(ctx, value));
        errors.add(new NumberRestrictionValidator(schema).validate(ctx, value));
        errors.add(validateByType(ctx, value));
        return errors;
    }

    private ValidationErrors validateByType(ValidationContext ctx, Object value) {

        if (schema.getType() == null) {
            return null;
        }

        try {
            switch (schema.getType()) {
                case "number":
                    return new NumberValidator(schema).validate(ctx.schemaType("number"), value);
                case "integer":
                    return new IntegerValidator(schema).validate(ctx.schemaType("integer"), value);
                case "string":
                    return new StringValidator(schema).validate(ctx.schemaType("string"), value);
                case "boolean":
                    return new BooleanValidator(schema).validate(ctx.schemaType("boolean"), value);
                case "array":
                    return new ArrayValidator(api, schema).validate(ctx.schemaType("array"), value);
                case "object":
                    return new ObjectValidator(api, schema).validate(ctx.schemaType("object"), value);
                default:
                    throw new RuntimeException("Should not happen! " + schema.getType());
            }
        } catch (Exception e) {
            return ValidationErrors.create(ctx, String.format("%s is not of %s format.", value, schema.getType()));
        }
    }

    /**
     * Unwrap or read value in case of InputStream or Body objects
     *
     */
    private Object resolveValueAndParseJSON(Object obj) throws IOException {

        if (obj instanceof Body)
            return ((Body)obj).getJson();

        // Just temp to make sure there is no inputstream anymore! Can be deleted later!
        if (obj instanceof InputStream) {
            throw new RuntimeException("InputStream!");
        }

        return obj;
    }
}
