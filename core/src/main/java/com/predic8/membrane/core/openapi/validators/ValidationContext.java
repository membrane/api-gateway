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

import com.predic8.membrane.core.openapi.model.*;

import static com.predic8.membrane.core.openapi.validators.ValidationContext.ValidatedEntityType.*;

public class ValidationContext {

    private String method;
    private String path;
    private String uriTemplate;
    private String jsonPointer = "";
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
        this.jsonPointer = ctx.jsonPointer;
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
        return jsonPointer;
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

    public String getLocationForRequest() {
        return getLocation("REQUEST");
    }

    public String getLocationForResponse() {
        return getLocation("RESPONSE");
    }

    private String getLocation(String message) {
        StringBuilder sb = new StringBuilder();
        sb.append(message);
        sb.append("/");

        if(validatedEntityType == null)
            return sb.toString();

        if (validatedEntityType.equals(QUERY_PARAMETER)) {
            sb.append(validatedEntityType.name());
            appendValidatedEntity(sb);
        } else if (validatedEntityType.equals(PATH_PARAMETER)) {
            sb.append(validatedEntityType.name());
            appendValidatedEntity(sb);
        } else if (validatedEntityType.equals(MEDIA_TYPE)) {
            sb.append("HEADER/Content-Type");
        } else {
            sb.append(validatedEntityType.name());
            if (!jsonPointer.isEmpty()) {
                sb.append("#");
                sb.append(getJSONpointer());
            }
        }

        return sb.toString();
    }

    private void appendValidatedEntity(StringBuilder sb) {
        if (validatedEntity != null) {
            sb.append("/");
            sb.append(validatedEntity);
        }
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

    public ValidationContext entityType(ValidatedEntityType type) {
        ValidationContext ctx = this.deepCopy();
        ctx.validatedEntityType = type;
        return ctx;
    }

    public ValidationContext entity(String entity) {
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
        ctx.jsonPointer = ctx.jsonPointer + "/" + segment;
        return ctx;
    }

    public enum ValidatedEntityType {
        PATH("path"),
        METHOD("method"),
        PATH_PARAMETER("path parameter"),
        QUERY_PARAMETER("query parameter"),
        HEADER_PARAMETER("header parameter"),
        BODY("body"),
        FIELD("field"),
        PROPERTY("property"),
        MEDIA_TYPE("media type");

        public final String name;
        ValidatedEntityType(String s) {
            this.name = s;
        }
    }

    @Override
    public String toString() {
        return "ValidationContext{" +
                "method='" + method + '\'' +
                ", path='" + path + '\'' +
                ", uriTemplate='" + uriTemplate + '\'' +
                ", xpointer='" + jsonPointer + '\'' +
                ", schemaType='" + schemaType + '\'' +
                ", complexType='" + complexType + '\'' +
                ", validatedEntityType=" + validatedEntityType +
                ", validatedEntity='" + validatedEntity + '\'' +
                ", statusCode=" + statusCode +
                '}';
    }
}
