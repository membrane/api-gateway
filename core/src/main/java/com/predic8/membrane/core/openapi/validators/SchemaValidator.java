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
import com.predic8.membrane.core.util.xml.parser.XmlParseException;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.SpecVersion;
import io.swagger.v3.oas.models.media.Schema;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.predic8.membrane.core.openapi.util.SchemaUtil.getSchemaNameFromRef;
import static com.predic8.membrane.core.openapi.validators.ValidationContext.ValidatedEntityType.BODY;
import static com.predic8.membrane.core.openapi.validators.ValidationContext.ValidatedEntityType.QUERY_PARAMETER;
import static com.predic8.membrane.core.openapi.validators.ValidationError.v;

public class SchemaValidator implements JsonSchemaValidator {

    private static final Logger log = LoggerFactory.getLogger(SchemaValidator.class.getName());

    private static final com.fasterxml.jackson.databind.ObjectMapper CONTENT_MAPPER = new com.fasterxml.jackson.databind.ObjectMapper();

    @SuppressWarnings("rawtypes")
    final private Schema schema;
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

        var errors = new ValidationErrors();

        if (obj == null)
            return errors.add(ctx, "Got null to validate!");

        Object value;
        try {
            if (ctx.isXML()) {
                if (obj instanceof Body body) {
                    // Top-level call: convert XML string → JsonNode guided by the schema
                    value = new XmlToJsonConverter(api).convert(body.asString(), schema);
                } else {
                    // Recursive call: obj is already a JsonNode produced by the converter
                    value = obj;
                }
            } else {
                value = resolveValueAndParseJSON(obj);
            }
        } catch (MixedContentException | MultipleElementsException e) {
            return errors.add(new ValidationError(ctx.entityType(BODY), e.getMessage()));
        } catch (SAXException | XmlParseException e) {
            log.info("Cannot parse XML body. " + e);
            return errors.add(new ValidationError(ctx.entityType(BODY), "Request body cannot be parsed as XML"));
        } catch (IOException e) {
            log.info("Cannot parse body. " + e);
            return errors.add(new ValidationError(ctx.statusCode(400).entityType(BODY).entity("REQUEST"), "Request body cannot be parsed as JSON"));
        }

        // The $ref has to be resolved before the composition keywords are read: a schema object that
        // is just a $ref carries none of them, they belong to the schema it points at. See #3119.
        if (schema.get$ref() == null)
            return errors.add(validateKeywords(ctx, schema, obj, value));

        String name = getSchemaNameFromRef(schema);

        // In JSON Schema 2020-12 (OAS 3.1 and later) $ref is an ordinary applicator: keywords next
        // to it are applied alongside the schema it points at. Validating both and adding up the
        // errors is exactly that. OAS 3.0 ignores them instead. See #3188.
        //
        // They belong to the referring schema, not to the referenced component: they keep the
        // context of the reference, and they are validated even when the branch is truncated below.
        ValidationErrors siblingErrors = appliesSiblings(schema)
                ? validateKeywords(ctx.visitRef(name), schema, obj, value)
                : null;

        // This schema was already resolved for this very value, so resolving it again cannot make
        // progress: stop descending instead of recursing forever. A recursive schema that follows
        // the instance (Node.next -> Node) reaches a new value on every level and is validated
        // there; only a cycle that closes on the same value is truncated, and it contributes no
        // errors.
        if (ctx.hasVisited(name))
            return errors.add(siblingErrors);

        Schema target = SchemaUtil.getSchemaFromRef(api, schema.get$ref());
        if (target == null)
            throw new RuntimeException("Should not happen!");

        errors.add(validateKeywords(ctx.complexType(name).visitRef(name), target, obj, value));
        return errors.add(notReportedYet(errors, siblingErrors));
    }

    /**
     * A keyword next to a $ref can repeat an assertion of the schema it points at, e.g. require the
     * same property. Reporting the identical error once per schema would only be noise.
     *
     * @return the errors that are not already in reported
     */
    private static @Nullable ValidationErrors notReportedYet(ValidationErrors reported, @Nullable ValidationErrors errors) {
        if (errors == null)
            return null;
        var result = new ValidationErrors();
        errors.getErrors().stream()
                .filter(error -> reported.getErrors().stream().noneMatch(r -> isSame(r, error)))
                .forEach(result::add);
        return result;
    }

    private static boolean isSame(ValidationError a, ValidationError b) {
        return Objects.equals(a.getMessage(), b.getMessage())
               && Objects.equals(location(a), location(b));
    }

    private static @Nullable String location(ValidationError error) {
        return error.getContext() != null ? error.getContext().getLocationForRequest() : null;
    }

    /**
     * Validates the value against every keyword of the schema except $ref, which the caller has
     * resolved already.
     */
    @SuppressWarnings("rawtypes")
    private ValidationErrors validateKeywords(ValidationContext ctx, Schema schema, Object obj, Object value) {

        var errors = new ValidationErrors();

        // A nullable schema accepts null itself, so validation stops here instead of descending into
        // allOf/anyOf/oneOf/not subschemas, which are typically not nullable and would reject it. See #3119.
        if ((value == null || value instanceof NullNode) && isNullable(schema))
            return errors;

        if (schema.getAllOf() != null)
            errors.add(new AllOfValidator(api, schema).validate(ctx, obj));

        // A discriminator selects the subschema itself, so brute forcing anyOf/oneOf on top of it
        // would only produce spurious "more than one subschema is valid" errors. ObjectValidator
        // does the dispatch.
        if (schema.getDiscriminator() == null) {
            if (schema.getAnyOf() != null)
                errors.add(new AnyOfValidator(api, schema).validate(ctx, obj));

            if (schema.getOneOf() != null)
                errors.add(new OneOfValidator(api, schema).validate(ctx, obj));
        }

        if (schema.getNot() != null)
            errors.add(new NotValidator(api, schema).validate(ctx, obj));

        errors.add(new NumberRestrictionValidator(schema).validate(ctx, value));
        errors.add(validateByType(ctx, schema, value));
        errors.add(validateContentSchema(ctx, schema, value));
        return errors;
    }

    /**
     * Validates the JSON Schema 2020-12 string content keywords (emphasized by OpenAPI 3.2): when a
     * string carries content of another media type ({@code contentMediaType}, optionally
     * {@code contentEncoding: base64}) the decoded content is parsed and validated against
     * {@code contentSchema}. Only JSON content media types are validated; others are left untouched.
     */
    @SuppressWarnings("rawtypes")
    private ValidationErrors validateContentSchema(ValidationContext ctx, Schema schema, Object value) {
        if (schema.getContentSchema() == null || schema.getContentMediaType() == null)
            return null;
        String text = asString(value);
        if (text == null || !isJsonMediaType(schema.getContentMediaType()))
            return null;

        String encoding = schema.getContentEncoding();
        if ("base64".equals(encoding) || "base64url".equals(encoding)) {
            var decoder = "base64url".equals(encoding)
                    ? java.util.Base64.getUrlDecoder()
                    : java.util.Base64.getMimeDecoder();
            try {
                text = new String(decoder.decode(text), java.nio.charset.StandardCharsets.UTF_8);
            } catch (IllegalArgumentException e) {
                return ValidationErrors.error(ctx, "The string is not valid base64 content.");
            }
        }

        com.fasterxml.jackson.databind.JsonNode content;
        try {
            content = CONTENT_MAPPER.readTree(text);
        } catch (IOException e) {
            return ValidationErrors.error(ctx, "The string content is not valid %s.".formatted(schema.getContentMediaType()));
        }
        return new SchemaValidator(api, schema.getContentSchema()).validate(ctx, content);
    }

    private static @Nullable String asString(Object value) {
        if (value instanceof com.fasterxml.jackson.databind.JsonNode node)
            return node.isTextual() ? node.textValue() : null;
        return value instanceof String s ? s : null;
    }

    private static boolean isJsonMediaType(String mediaType) {
        String mt = mediaType.toLowerCase();
        return mt.equals("application/json") || mt.endsWith("+json");
    }

    /**
     * @return true if keywords next to the $ref have to be validated. Only from OAS 3.1 on: 3.0
     * ignores them, and the parser is no help there because it keeps a sibling type while dropping
     * a sibling nullable, readOnly or required.
     */
    @SuppressWarnings("rawtypes")
    private static boolean appliesSiblings(Schema schema) {
        return schema.getSpecVersion() != SpecVersion.V30 && hasSiblings(schema);
    }

    /**
     * @return true if the schema carries keywords besides the $ref. Compares against a schema of
     * the same class that has nothing but the same $ref, because Schema does not expose its keywords
     * as a map and Schema.equals() compares the class. A schema that cannot be instantiated counts
     * as having siblings, which validates more rather than less.
     * <p>
     * Keywords a no-arg constructor sets itself are invisible to the comparison, e.g. the type of a
     * StringSchema. That only affects OAS 3.0, where such subclasses are used and keywords next to a
     * $ref are ignored anyway; 3.1 and later parse into JsonSchema, which starts out empty.
     */
    @SuppressWarnings("rawtypes")
    static boolean hasSiblings(Schema schema) {
        Schema bare;
        try {
            bare = schema.getClass().getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            log.debug("Cannot instantiate {} to look for keywords next to a $ref.", schema.getClass(), e);
            return true;
        }
        bare.setSpecVersion(schema.getSpecVersion());
        bare.set$ref(schema.get$ref());
        return !bare.equals(schema);
    }

    @SuppressWarnings("rawtypes")
    private static boolean isNullable(Schema schema) {
        return (schema.getNullable() != null && schema.getNullable()) || (schema.getTypes() != null && schema.getTypes().contains("null")) ||
               (schema.getType() != null && schema.getType().equals("null"));
    }

    @SuppressWarnings("rawtypes")
    private ValidationErrors validateByType(ValidationContext ctx, Schema schema, Object value) {

        // For the same OpenAPI content version 3.0.X and 3.1.X can deliver different values
        // e.g. an array in the QueryParameter
        var type = schema.getType();

        if (schemaHasNoTypeAndTypes(schema, type)) {
            return validateMultipleTypes(List.of(STRING, NUMBER, INTEGER, BOOLEAN, ARRAY, OBJECT, NULL), ctx, schema, value);
        }

        // type in schema has only one type
        if (type != null)
            return validateSingleType(ctx, schema, value, type);

        // At that point: schema.types is used
        return validateMultipleTypes(new ArrayList<String>(schema.getTypes()), ctx, schema, value);
    }

    @SuppressWarnings("rawtypes")
    private @Nullable ValidationErrors validateMultipleTypes(List<String> types, ValidationContext ctx, Schema schema, Object value) {
        var typeOfValue = getTypeOfValue(types, value);

        var errors = getTypeNotMatchError(types, ctx, value, typeOfValue);
        if (errors != null) return errors;

        return validateSingleType(ctx, schema, value, typeOfValue);

    }

    /**
     * <p>If the type of the value does not match any of the specified types, an error is created.
     * For example, if the types are ["number", "null"] and the value is "Manila" (a string),
     * a validation error is returned.
     *
     * @param types       the list of allowed types in the schema, e.g., ["number", "null"]
     * @param ctx         the validation context
     * @param value       the value being validated
     * @param typeOfValue the determined type of the value
     * @return a ValidationErrors object if there is a type mismatch, or null if the type is valid
     */
    @Nullable ValidationErrors getTypeNotMatchError(List<String> types, ValidationContext ctx, Object value, String typeOfValue) {

        for (String type : types) {
            if (type.equals(typeOfValue))
                return null;
            // In a query parameter, there are no quotes for strings e.g. ?q=foo so even a number ?q=123 is a valid string
            // Since this method can be called for query strings and bodies the ctx is used to determine the entity type
            if (QUERY_PARAMETER.equals(ctx.getValidatedEntityType())) {
                if ("string".equals(type)) {
                    return null;
                }
            }
        }

        return ValidationErrors.error(ctx, mask -> "%s is of type %s which does not match any of %s".formatted(v(value, mask), typeOfValue, types));
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
        var typeOfValue = getType(value);
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


    @SuppressWarnings("rawtypes")
    private ValidationErrors validateSingleType(ValidationContext ctx, Schema schema, Object value, String type) {
        try {
            return switch (type) {
                case NULL -> new NullValidator().validate(ctx, value);
                case NUMBER -> new NumberValidator().validate(ctx, value);
                case INTEGER -> new IntegerValidator().validate(ctx, value);
                case STRING -> new StringValidator(schema).validate(ctx, value);
                case BOOLEAN -> new BooleanValidator().validate(ctx, value);
                case ARRAY -> new ArrayValidator(api, schema).validate(ctx, value);
                case OBJECT -> new ObjectValidator(api, schema).validate(ctx, value);
                default -> throw new RuntimeException("Should not happen! " + type);
            };
        } catch (Exception e) {
            return ValidationErrors.error(ctx, mask -> "%s is not of %s format.".formatted(v(value, mask), type));
        }
    }

    @SuppressWarnings("rawtypes")
    private static boolean schemaHasNoTypeAndTypes(Schema schema, String type) {
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