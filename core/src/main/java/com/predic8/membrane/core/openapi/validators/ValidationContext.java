package com.predic8.membrane.core.openapi.validators;

import com.predic8.membrane.core.openapi.model.*;

public class ValidationContext {

    private String method;
    private String path;
    private String uriTemplate;
    private String xpointer = "";
    private String schemaType;
    private String complexType;
    private ValidatedEntityType validatedEntityType;
    private String validatedEntity;
    private int statusCode;

    public static ValidationContext fromRequest(Request request) {
        ValidationContext ctx = new ValidationContext();
        ctx.method = request.getMethod();
        ctx.path = request.getPath();
        return ctx;
    }

    public ValidationContext(ValidationContext ctx) {
        this.method = ctx.method;
        this.path = ctx.path;
        this.uriTemplate = ctx.uriTemplate;
        this.xpointer = ctx.xpointer;
        this.schemaType = ctx.schemaType;
        this.complexType = ctx.complexType;
        this.validatedEntityType = ctx.validatedEntityType;
        this.validatedEntity = ctx.validatedEntity;
        this.statusCode = ctx.statusCode;
    }

    public ValidationContext() {
    }

    public static ValidationContext create() {
        return new ValidationContext();
    }

    public ValidationContext deepCopy() {
        return new ValidationContext(this);
    }

    public String getJSONpointer() {
        return xpointer;
    }

    public String getMethod() {
        return method;
    }

    public ValidatedEntityType getValidatedEntityType() {
        return validatedEntityType;
    }

    public String getValidatedEntity() {
        return validatedEntity;
    }

    public String getSchemaType() {
        return schemaType;
    }

    public String getComplexType() {
        return complexType;
    }

    public ValidationContext path(String path) {
        ValidationContext ctx = this.deepCopy();
        ctx.path = path;
        return ctx;
    }

    public ValidationContext method(String method) {
        ValidationContext ctx = this.deepCopy();
        ctx.method = method;
        return ctx;
    }

    public ValidationContext uriTemplate(String uriTemplate) {
        ValidationContext ctx = this.deepCopy();
        ctx.uriTemplate = uriTemplate;
        return ctx;
    }

    public ValidationContext validatedEntityType(ValidatedEntityType type) {
        ValidationContext ctx = this.deepCopy();
        ctx.validatedEntityType = type;
        return ctx;
    }

    public ValidationContext validatedEntity(String entity) {
        ValidationContext ctx = this.deepCopy();
        ctx.validatedEntity = entity;
        return ctx;
    }

    public ValidationContext statusCode(int statusCode) {
        ValidationContext ctx = this.deepCopy();
        ctx.statusCode = statusCode;
        return ctx;
    }

    public ValidationContext schemaType(String type) {
        ValidationContext ctx = this.deepCopy();
        ctx.schemaType = type;
        return ctx;
    }

    public ValidationContext complexType(String type) {
        ValidationContext ctx = this.deepCopy();
        ctx.complexType = type;
        return ctx;
    }

    public String getPath() {
        return path;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getUriTemplate() {
        return uriTemplate;
    }

    public ValidationContext addJSONpointerSegment(String segment) {
        ValidationContext ctx = this.deepCopy();
        ctx.xpointer = ctx.xpointer + "/" + segment;
        return ctx;
    }

    public enum ValidatedEntityType {
        PATH, METHOD, PATH_PARAMETER, QUERY_PARAMETER, BODY, FIELD, PROPERTY;
    };

    @Override
    public String toString() {
        return "ValidationContext{" +
                "method='" + method + '\'' +
                ", path='" + path + '\'' +
                ", uriTemplate='" + uriTemplate + '\'' +
                ", xpointer='" + xpointer + '\'' +
                ", schemaType='" + schemaType + '\'' +
                ", complexType='" + complexType + '\'' +
                ", validatedEntityType=" + validatedEntityType +
                ", validatedEntity='" + validatedEntity + '\'' +
                ", statusCode=" + statusCode +
                '}';
    }
}
