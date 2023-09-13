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

import com.fasterxml.jackson.databind.node.*;
import com.predic8.membrane.core.openapi.model.*;
import com.predic8.membrane.core.openapi.util.*;
import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.media.*;
import org.slf4j.*;

import java.io.*;

import static com.predic8.membrane.core.openapi.util.SchemaUtil.*;
import static com.predic8.membrane.core.openapi.validators.ValidationContext.ValidatedEntityType.*;

public class SchemaValidator implements IJSONSchemaValidator {

    private static final Logger log = LoggerFactory.getLogger(SchemaValidator.class.getName());

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
            return errors.add(new ValidationError(ctx.statusCode(400).entityType(BODY).entity("REQUEST"), "Request body cannot be parsed as JSON"));
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
            // ctx.schemaType also deepcopies and is needed otherwise we'll lose schemaType information
            return getValidator().validate(ctx.schemaType(schema.getType()), value);
        } catch (Exception e) {
            return ValidationErrors.create(ctx, "%s is not of %s format.".formatted(value, schema.getType()));
        }
    }

    private IJSONSchemaValidator getValidator() {
        return switch (schema.getType()) {
            case "number" -> new NumberValidator();
            case "integer" -> new IntegerValidator();
            case "string" -> new StringValidator(schema);
            case "boolean" -> new BooleanValidator();
            case "array" -> new ArrayValidator(api, schema);
            case "object" -> new ObjectValidator(api, schema);
            default -> throw new RuntimeException("Should not happen! " + schema.getType());
        };
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
