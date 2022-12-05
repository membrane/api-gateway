package com.predic8.membrane.core.openapi.validators;

import java.util.*;

import static com.predic8.membrane.core.openapi.util.Utils.setFieldIfNotNull;

public class ValidationError {

    private final String message;
    private final ValidationContext ctx;

    public ValidationError(String message) {
        this.message = message;
        this.ctx = null;
    }

    public ValidationError(ValidationContext ctx, String message) {
        this.message = message;
        this.ctx = ctx;
    }

    public String getMessage() {
        return message;
    }

    public ValidationContext getContext() {
        return ctx;
    }

    /**
     * Takes out all fields with null values
     */
    @SuppressWarnings("ConstantConditions")
    public Map<String,Object> getContentMap() {
        Map<String,Object> fields = new LinkedHashMap<>();
        setFieldIfNotNull(fields,"message", message);
        setFieldIfNotNull(fields,"method",ctx.getMethod());
        setFieldIfNotNull(fields,"uriTemplate",ctx.getUriTemplate());
        setFieldIfNotNull(fields,"path",ctx.getPath());
        setFieldIfNotNull(fields,"complexType",ctx.getComplexType());
        setFieldIfNotNull(fields,"schemaType",ctx.getSchemaType());

        return fields;
    }

    @Override
    public String toString() {
        if (ctx == null)
            return message;
        return String.format("%s %s %s: %s",ctx.getMethod(), ctx.getPath(), ctx.getJSONpointer(), message);
    }
}
