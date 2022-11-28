package com.predic8.membrane.core.openapi.validators;

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

    public ValidationContext getValidationContext() {
        return ctx;
    }

    @Override
    public String toString() {
        if (ctx == null)
            return message;
        return String.format("%s %s %s: %s",ctx.getMethod(), ctx.getPath(), ctx.getJSONpointer(), message);
    }
}
