/*
 *  Copyright 2026 predic8 GmbH, www.predic8.com
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.openapi.OpenAPIParsingException;
import com.predic8.membrane.core.openapi.model.JsonBody;
import com.predic8.membrane.core.openapi.model.Message;
import com.predic8.membrane.core.openapi.util.SchemaUtil;
import com.predic8.membrane.core.util.xml.parser.XmlParseException;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.SpecVersion;
import io.swagger.v3.oas.models.media.Encoding;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.mail.internet.ContentType;
import jakarta.mail.internet.ParseException;
import org.apache.commons.fileupload.MultipartStream;
import org.apache.commons.fileupload.ParameterParser;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static com.predic8.membrane.core.http.Header.CONTENT_TRANSFER_ENCODING;
import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.openapi.util.SchemaUtil.isBinaryString;
import static com.predic8.membrane.core.openapi.util.SchemaUtil.isObjectOrArray;
import static com.predic8.membrane.core.openapi.validators.ValidationContext.Content.XML;
import static com.predic8.membrane.core.openapi.validators.ValidationContext.ValidatedEntityType.BODY;
import static java.lang.Boolean.FALSE;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Validates a <code>multipart/form-data</code> message body against an OpenAPI media type.
 * <p>
 * The body is split into its parts using the boundary from the <code>Content-Type</code> header.
 * Each part is matched against a property of the (object) schema by its <code>Content-Disposition</code>
 * name. Parts that carry structured data (e.g. <code>application/json</code>) are validated against the
 * property's schema. Parts that are declared as binary file uploads (<code>type: string, format: binary</code>)
 * are treated as opaque and only checked for presence.
 * <p>
 * The effective content type of a part is determined - in order of precedence - by the part's own
 * <code>Content-Type</code> header, the <code>encoding</code> section of the media type, and finally the
 * default derived from the property's schema. A <code>Content-Transfer-Encoding: base64</code> part is
 * decoded before validation.
 */
public class MultipartFormDataValidator {

    private static final Logger log = LoggerFactory.getLogger(MultipartFormDataValidator.class);

    private final OpenAPI api;

    public MultipartFormDataValidator(OpenAPI api) {
        this.api = api;
    }

    public ValidationErrors validate(ValidationContext ctx, MediaType mediaType, Message<?, ?> message) {
        ctx = ctx.entityType(BODY);
        var err = new ValidationErrors();

        if (mediaType.getSchema() == null) {
            // Without a schema there is nothing to validate the parts against.
            return err;
        }

        var declaredSchema = mediaType.getSchema();
        var schema = SchemaUtil.resolveRef(api, declaredSchema);
        if (schema == null) {
            return err.add(ctx, "Cannot resolve the schema reference '%s'.".formatted(mediaType.getSchema().get$ref()));
        }

        var boundary = getBoundary(message);
        if (boundary == null) {
            return err.add(ctx, "Content-Type 'multipart/form-data' is missing the required 'boundary' parameter.");
        }

        Map<String, List<FormPart>> parts;
        try {
            parts = parseParts(message, boundary);
        } catch (Exception e) {
            log.info("Cannot parse multipart/form-data body.", e);
            return err.add(ctx, "The multipart/form-data body cannot be parsed: " + e.getMessage());
        }

        err.add(validateRequiredParts(ctx, schema, parts));
        err.add(validateParts(ctx, schema, mediaType.getEncoding(), parts));
        // In OAS 3.1+, keywords alongside $ref apply as another schema to the same body.
        // Keep their property rules separate from the referenced component's rules.
        if (declaredSchema.get$ref() != null && declaredSchema.getSpecVersion() != SpecVersion.V30
                && SchemaValidator.hasSiblings(declaredSchema)) {
            err.add(validateRequiredParts(ctx, declaredSchema, parts));
            err.add(validateParts(ctx, declaredSchema, mediaType.getEncoding(), parts));
        }
        return err;
    }

    private String getBoundary(Message<?, ?> message) {
        var ct = message.getMediaType();
        if (ct == null)
            return null;
        return ct.getParameter("boundary");
    }

    @SuppressWarnings("deprecation")
    private Map<String, List<FormPart>> parseParts(Message<?, ?> message, String boundary) throws IOException {
        Map<String, List<FormPart>> parts = new HashMap<>();
        byte[] body = message.getBody().asBytes();

        var mps = new MultipartStream(new ByteArrayInputStream(body), boundary.getBytes(UTF_8));
        boolean nextPart = mps.skipPreamble();
        while (nextPart) {
            var header = new Header(mps.readHeaders());
            var baos = new ByteArrayOutputStream();
            mps.readBodyData(baos);

            var part = new FormPart(header, decodeIfNecessary(header, baos.toByteArray()));
            if (part.name == null)
                throw new IOException("a part is missing the required Content-Disposition 'name' parameter.");
            parts.computeIfAbsent(part.name, k -> new ArrayList<>()).add(part);

            nextPart = mps.readBoundary();
        }
        return parts;
    }

    private byte[] decodeIfNecessary(Header header, byte[] content) {
        var cte = header.getFirstValue( CONTENT_TRANSFER_ENCODING);
        if (cte != null && cte.trim().equalsIgnoreCase("base64")) {
            return Base64.getMimeDecoder().decode(content);
        }
        return content;
    }

    @SuppressWarnings("rawtypes")
    private ValidationErrors validateRequiredParts(ValidationContext ctx, Schema schema, Map<String, List<FormPart>> parts) {
        var err = new ValidationErrors();
        if (schema.getRequired() == null)
            return err;
        for (var required : schema.getRequired()) {
            if (!parts.containsKey(required.toString()))
                err.add(ctx.statusCode(400), "Required multipart property '%s' is missing.".formatted(required));
        }
        return err;
    }

    @SuppressWarnings("rawtypes")
    private ValidationErrors validateParts(ValidationContext ctx, Schema schema, Map<String, Encoding> encodings, Map<String, List<FormPart>> parts) {
        var err = new ValidationErrors();
        // A schema without properties may still be closed (additionalProperties: false), so fall back to
        // an empty map and let the loop below reject unexpected parts rather than returning early.
        Map<String, Schema> properties = schema.getProperties() != null ? schema.getProperties() : Map.of();

        var patterns = compilePatternProperties(schema);

        for (var entry : parts.entrySet()) {
            var name = entry.getKey();

            var schemas = collectMatchingSchemas(properties, name, patterns);
            if (schemas.isEmpty()) {
                // An undeclared part is validated against an additionalProperties schema.
                var additional = SchemaUtil.getAdditionalProperties(schema);
                if (FALSE.equals(additional))
                    err.add(ctx.statusCode(400), "The multipart/form-data body contains an unexpected property '%s'.".formatted(name));
                if (!(additional instanceof Schema<?> additionalSchema))
                    continue;
                schemas.add(additionalSchema);
            }

            // Choose decoding once, in stable order, without letting a typeless constraint hide a type.
            var resolvedSchemas = schemas.stream().map(this::resolveSchema).toList();
            var partSchema = decodingSchema(resolvedSchemas);
            boolean array = SchemaUtil.isArray(partSchema);
            boolean repeatedItems = treatsPartsAsArrayItems(array, resolvedSchemas);
            var partCtx = ctx.addJSONpointerSegment(name);
            // Array occurrences represent one array value; a scalar schema cannot validate its items
            // instead of the array, which would incorrectly allow incompatible types to pass.
            if (resolvedSchemas.stream().anyMatch(s -> !allowsArrayChoice(s, array))) {
                err.add(partCtx.statusCode(400), "Multipart property '%s' has incompatible array and non-array schemas.".formatted(name));
                continue;
            }
            if (array)
                for (var candidate : resolvedSchemas)
                    err.add(validateArrayCardinality(partCtx, candidate, entry.getValue().size()));

            // A field name may occur multiple times (array items / multi-file fields), so validate every occurrence.
            var occurrences = entry.getValue();
            for (int i = 0; i < occurrences.size(); i++) {
                var part = occurrences.get(i);

                // If the part carries an explicit Content-Type it must conform to the declared
                // encoding; otherwise the body is not validated against a content type it was not sent with.
                var contentTypeErrors = validateEncodingContentType(partCtx, name, part, encodings);
                if (!contentTypeErrors.isEmpty()) {
                    err.add(contentTypeErrors);
                    continue;
                }

                var occurrenceSchemas = collectOccurrenceSchemas(schemas, array, resolvedSchemas, i, repeatedItems);
                if (occurrenceSchemas.isEmpty())
                    continue;
                var decodingSchema = decodingSchema(occurrenceSchemas.stream().map(this::resolveSchema).toList());
                err.add(validatePart(partCtx, occurrenceSchemas, decodingSchema,
                        contentTypeOf(name, part, decodingSchema, encodings), hasDeclaredContentType(name, encodings), part));
            }
        }
        return err;
    }

    private @NonNull ArrayList<Schema> collectOccurrenceSchemas(ArrayList<Schema> schemas, boolean array, List<Schema> resolvedSchemas, int i, boolean repeatedItems) {
        var occurrenceSchemas = new ArrayList<Schema>();
        for (int j = 0; j < schemas.size(); j++) {
            var candidate = array ? occurrenceSchema(resolvedSchemas.get(j), i, repeatedItems) : schemas.get(j);
            // An item after prefixItems without items is unconstrained.
            if (candidate != null)
                occurrenceSchemas.add(candidate);
        }
        return occurrenceSchemas;
    }

    private static boolean treatsPartsAsArrayItems(boolean array, List<Schema> resolvedSchemas) {
        return array && resolvedSchemas.stream()
                .anyMatch(s -> s.getItems() != null || s.getPrefixItems() != null);
    }

    private static @NonNull ArrayList<Schema> collectMatchingSchemas(Map<String, Schema> properties, String name, LinkedHashMap<Pattern, Schema> patterns) {
        // Explicit properties and every matching pattern apply together.
        var schemas = new ArrayList<Schema>();
        if (properties.containsKey(name))
            schemas.add(properties.get(name));
        patterns.forEach((pattern, matchedSchema) -> {
            if (pattern.matcher(name).find())
                schemas.add(matchedSchema);
        });
        return schemas;
    }

    private static @NonNull LinkedHashMap<Pattern, Schema> compilePatternProperties(Schema schema) {
        Map<String, Schema> patternProperties = schema.getPatternProperties();
        var patterns = new LinkedHashMap<Pattern, Schema>();
        if (patternProperties != null)
            patternProperties.entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> {
                        try {
                            patterns.put(Pattern.compile(entry.getKey()), entry.getValue());
                        } catch (PatternSyntaxException e) {
                            throw new OpenAPIParsingException("Invalid multipart schema pattern '%s': %s"
                                    .formatted(entry.getKey(), e.getDescription()));
                        }
                    });
        return patterns;
    }

    @SuppressWarnings("rawtypes")
    private Schema resolveSchema(Schema schema) {
        var resolved = SchemaUtil.resolveRef(api, schema);
        if (resolved == null)
            throw new OpenAPIParsingException("Cannot resolve multipart schema reference '%s'.".formatted(schema.get$ref()));
        return resolved;
    }

    @SuppressWarnings("rawtypes")
    private Schema decodingSchema(List<Schema> schemas) {
        var typed = schemas.stream().filter(s -> SchemaUtil.getEffectiveType(s) != null).toList();
        // Prefer a schema whose chosen type is allowed by every other typed constraint.
        var preferred = typed.stream().filter(candidate -> typed.stream().allMatch(s ->
                        allowsType(s, SchemaUtil.getEffectiveType(candidate))))
                .findFirst();
        if (preferred.isPresent())
            return preferred.get();
        if (typed.isEmpty())
            return schemas.getFirst();
        var types = typed.getFirst().getTypes();
        if (types != null)
            for (Object type : types)
                if (!"null".equals(type) && typed.stream().allMatch(s -> allowsType(s, type.toString())))
                    return new Schema<>().type(type.toString());
        return typed.getFirst();
    }

    private boolean allowsType(Schema schema, String type) {
        return type.equals(schema.getType()) || schema.getTypes() != null && schema.getTypes().contains(type);
    }

    private boolean allowsArrayChoice(Schema schema, boolean array) {
        if (SchemaUtil.getEffectiveType(schema) == null)
            return true;
        if (array)
            return allowsType(schema, "array");
        if (schema.getType() != null && !"array".equals(schema.getType()) && !"null".equals(schema.getType()))
            return true;
        return schema.getTypes() != null && schema.getTypes().stream()
                .anyMatch(type -> !"array".equals(type) && !"null".equals(type));
    }

    /**
     * Array constraints without items do not constrain individual occurrences. If none of the
     * applicable arrays defines items or prefixItems, retain the existing JSON-array-per-part behavior.
     */
    @SuppressWarnings("rawtypes")
    private Schema occurrenceSchema(Schema arraySchema, int index, boolean repeatedItems) {
        if (arraySchema.getItems() == null && arraySchema.getPrefixItems() == null)
            return !repeatedItems && SchemaUtil.isArray(arraySchema) || FALSE.equals(arraySchema.getBooleanSchemaValue())
                    ? arraySchema : null;
        var item = SchemaUtil.itemSchema(arraySchema, index);
        return item == null ? null : resolveSchema(item);
    }

    @SuppressWarnings("rawtypes")
    private ValidationErrors validateArrayCardinality(ValidationContext ctx, Schema arraySchema, int count) {
        var err = new ValidationErrors();
        if (arraySchema.getMinItems() != null && count < arraySchema.getMinItems())
            err.add(ctx.statusCode(400), "Array has %d items. This is less than minItems of %d.".formatted(count, arraySchema.getMinItems()));
        if (arraySchema.getMaxItems() != null && count > arraySchema.getMaxItems())
            err.add(ctx.statusCode(400), "Array has %d items. This is more than maxItems of %d.".formatted(count, arraySchema.getMaxItems()));
        return err;
    }

    private ValidationErrors validateEncodingContentType(ValidationContext ctx, String name, FormPart part, Map<String, Encoding> encodings) {
        var err = new ValidationErrors();
        if (part.contentType == null)
            return err;
        try {
            final var contentType = new ContentType(part.contentType);
            if (contentType.getPrimaryType() == null || contentType.getPrimaryType().isBlank()
                    || contentType.getSubType() == null || contentType.getSubType().isBlank())
                return err.add(ctx.statusCode(400), "Part '%s' has an invalid Content-Type '%s'.".formatted(name, part.contentType));
        } catch (ParseException e) {
            return err.add(ctx.statusCode(400), "Part '%s' has an invalid Content-Type '%s'.".formatted(name, part.contentType));
        }
        // A media range like application/* would match any declared encoding, but a part has one concrete type.
        if (isMediaRange(part.contentType))
            return err.add(ctx.statusCode(400), "Part '%s' has the media range '%s' as Content-Type, a concrete media type is required.".formatted(name, part.contentType));
        if (encodings == null)
            return err;
        var encoding = encodings.get(name);
        if (encoding == null || encoding.getContentType() == null)
            return err;
        if (!isOfAnyMediaType(encoding.getContentType(), part.contentType))
            err.add(ctx.statusCode(400), "Part '%s' has Content-Type '%s' but the encoding requires '%s'.".formatted(name, part.contentType, encoding.getContentType()));
        return err;
    }

    @SuppressWarnings("rawtypes")
    private ValidationErrors validatePart(ValidationContext ctx, List<Schema> schemas, Schema propertySchema, String contentType, boolean declaredContentType, FormPart part) {
        var err = new ValidationErrors();

        // A false schema (e.g. items: false or prefixItems: [false]) allows no part at all. Without a
        // type it would fall through the content-type dispatch below unvalidated. See #3479.
        if (schemas.stream().anyMatch(s -> FALSE.equals(resolveSchema(s).getBooleanSchemaValue())))
            return err.add(ctx.statusCode(400), "The schema is false and does not allow any value.");

        // Binary file uploads (type: string, format: binary/byte) are opaque, only their presence is checked.
        if (isBinaryString(propertySchema))
            return validateDecodedPart(ctx, schemas,
                    JsonNodeFactory.instance.textNode(new String(part.content, UTF_8)));

        if (isJson(contentType)) {
            try {
                return err.add(validateDecodedPart(ctx, schemas, new JsonBody(new String(part.content, UTF_8)).getPayload()));
            } catch (Exception e) {
                return err.add(ctx.statusCode(400), "Part '%s' is declared as %s but its content could not be parsed as JSON.".formatted(part.name, contentType));
            }
        }

        // XML parts are converted to JSON (guided by the schema) and validated by the same
        // SchemaValidator. The conversion is done here so parse failures can name the part.
        if (isXML(contentType)) {
            return err.add(validateDecodedPart(ctx.content(XML), schemas, new String(part.content, UTF_8), part.name, contentType));
        }

        // Scalar parts (e.g. text/plain, the default for primitives) are validated by parsing the
        // raw text into the declared type and validating that value against the schema.
        if (SchemaUtil.isScalar(propertySchema) || !isObjectOrArray(api, propertySchema)) {
            return err.add(validateDecodedPart(ctx, schemas, SchemaUtil.parseScalar(new String(part.content, UTF_8), propertySchema)));
        }

        // Opaque content is accepted if the encoding declares it (e.g. octet-stream for a complex type).
        // A structured part must not escape its schema through a Content-Type chosen by the client.
        if (declaredContentType || !isObjectOrArray(api, propertySchema))
            return err;
        return err.add(ctx.statusCode(400), "Part '%s' has Content-Type '%s', which cannot be validated against the schema.".formatted(part.name, contentType));
    }

    @SuppressWarnings("rawtypes")
    private ValidationErrors validateDecodedPart(ValidationContext ctx, List<Schema> schemas, JsonNode value) {
        var errors = new ValidationErrors();
        // Reuse the same node: decoding separately could satisfy conflicting integer/string schemas.
        for (var schema : schemas) {
            // Keep the existing opaque-upload semantics, without skipping other matching constraints.
            if (!isBinaryString(resolveSchema(schema)))
                errors.add(new SchemaValidator(api, schema).validate(ctx, value));
        }
        return errors;
    }

    @SuppressWarnings("rawtypes")
    private ValidationErrors validateDecodedPart(ValidationContext ctx, List<Schema> schemas, String xml, String partName, String contentType) {
        var errors = new ValidationErrors();
        for (var schema : schemas) {
            if (isBinaryString(resolveSchema(schema)))
                continue;
            try {
                var json = new XmlToJsonConverter(api).convert(xml, schema);
                errors.add(new SchemaValidator(api, schema).validate(ctx, json));
            } catch (MixedContentException | MultipleElementsException | UndeclaredContentException e) {
                errors.add(ctx.statusCode(400), "Part '%s': %s".formatted(partName, e.getMessage()));
            } catch (SAXException | XmlParseException | IOException e) {
                errors.add(ctx.statusCode(400), "Part '%s' is declared as %s but its content could not be parsed as XML.".formatted(partName, contentType));
            }
        }
        return errors;
    }

    private boolean hasDeclaredContentType(String name, Map<String, Encoding> encodings) {
        if (encodings == null)
            return false;
        var encoding = encodings.get(name);
        return encoding != null && encoding.getContentType() != null;
    }

    @SuppressWarnings("rawtypes")
    private String contentTypeOf(String name, FormPart part, Schema propertySchema, Map<String, Encoding> encodings) {
        if (part.contentType != null)
            return part.contentType;
        if (encodings != null) {
            var encoding = encodings.get(name);
            if (encoding != null && encoding.getContentType() != null)
                return encoding.getContentType();
        }
        return defaultContentType(propertySchema);
    }

    @SuppressWarnings("rawtypes")
    private String defaultContentType(Schema schema) {
        // schema is already $ref-resolved by the caller.
        if (isBinaryString(schema))
            return APPLICATION_OCTET_STREAM;
        if (isObjectOrArray(api, schema))
            return APPLICATION_JSON;
        return TEXT_PLAIN;
    }

    /**
     * A single part of a parsed multipart/form-data body.
     */
    private static class FormPart {

        final String name;
        final String filename;
        final String contentType;
        final byte[] content;

        FormPart(Header header, byte[] content) {
            this.content = content;
            this.contentType = header.getContentType();

            var contentDisposition = header.getFirstValue("Content-Disposition");
            if (contentDisposition != null) {
                var parser = new ParameterParser();
                parser.setLowerCaseNames(true);
                Map<String, String> params = parser.parse(contentDisposition, new char[]{';'});
                this.name = params.get("name");
                this.filename = params.get("filename");
            } else {
                this.name = null;
                this.filename = null;
            }
        }
    }
}
