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

import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.openapi.model.JsonBody;
import com.predic8.membrane.core.openapi.model.Message;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Encoding;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.mail.internet.ContentType;
import org.apache.commons.fileupload.MultipartStream;
import org.apache.commons.fileupload.ParameterParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.openapi.validators.ValidationContext.ValidatedEntityType.BODY;
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

    @SuppressWarnings("rawtypes")
    public ValidationErrors validate(ValidationContext ctx, MediaType mediaType, Message<?, ?> message) {
        ctx = ctx.entityType(BODY);
        ValidationErrors errors = new ValidationErrors();

        Schema schema = mediaType.getSchema();
        if (schema == null) {
            // Without a schema there is nothing to validate the parts against.
            return errors;
        }

        String boundary = getBoundary(message);
        if (boundary == null) {
            return errors.add(ctx, "Content-Type 'multipart/form-data' is missing the required 'boundary' parameter.");
        }

        Map<String, FormPart> parts;
        try {
            parts = parseParts(message, boundary);
        } catch (Exception e) {
            log.warn("Cannot parse multipart/form-data body.", e);
            return errors.add(ctx, "The multipart/form-data body cannot be parsed: " + e.getMessage());
        }

        errors.add(validateRequiredParts(ctx, schema, parts));
        errors.add(validateParts(ctx, schema, mediaType.getEncoding(), parts));
        return errors;
    }

    private String getBoundary(Message<?, ?> message) {
        ContentType ct = message.getMediaType();
        if (ct == null)
            return null;
        return ct.getParameter("boundary");
    }

    @SuppressWarnings("deprecation")
    private Map<String, FormPart> parseParts(Message<?, ?> message, String boundary) throws IOException {
        Map<String, FormPart> parts = new HashMap<>();
        byte[] body = message.getBody().asString().getBytes(UTF_8);

        MultipartStream mps = new MultipartStream(new ByteArrayInputStream(body), boundary.getBytes(UTF_8));
        boolean nextPart = mps.skipPreamble();
        while (nextPart) {
            Header header = new Header(mps.readHeaders());
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            mps.readBodyData(baos);

            FormPart part = new FormPart(header, decodeIfNecessary(header, baos.toByteArray()));
            if (part.name != null)
                parts.put(part.name, part);

            nextPart = mps.readBoundary();
        }
        return parts;
    }

    private byte[] decodeIfNecessary(Header header, byte[] content) {
        String cte = header.getFirstValue("Content-Transfer-Encoding");
        if (cte != null && cte.trim().equalsIgnoreCase("base64")) {
            return Base64.getMimeDecoder().decode(content);
        }
        return content;
    }

    @SuppressWarnings("rawtypes")
    private ValidationErrors validateRequiredParts(ValidationContext ctx, Schema schema, Map<String, FormPart> parts) {
        ValidationErrors errors = new ValidationErrors();
        if (schema.getRequired() == null)
            return errors;
        for (Object required : schema.getRequired()) {
            if (!parts.containsKey(required.toString()))
                errors.add(ctx.statusCode(400), "Required multipart property '%s' is missing.".formatted(required));
        }
        return errors;
    }

    @SuppressWarnings("rawtypes")
    private ValidationErrors validateParts(ValidationContext ctx, Schema schema, Map<String, Encoding> encodings, Map<String, FormPart> parts) {
        ValidationErrors errors = new ValidationErrors();
        Map<String, Schema> properties = schema.getProperties();
        if (properties == null)
            return errors;

        for (Map.Entry<String, FormPart> entry : parts.entrySet()) {
            String name = entry.getKey();
            FormPart part = entry.getValue();

            Schema propertySchema = properties.get(name);
            if (propertySchema == null) {
                if (Boolean.FALSE.equals(schema.getAdditionalProperties()))
                    errors.add(ctx.statusCode(400), "The multipart/form-data body contains an unexpected property '%s'.".formatted(name));
                continue;
            }

            errors.add(validatePart(ctx.addJSONpointerSegment(name), propertySchema,
                    contentTypeOf(name, part, propertySchema, encodings), part));
        }
        return errors;
    }

    @SuppressWarnings("rawtypes")
    private ValidationErrors validatePart(ValidationContext ctx, Schema propertySchema, String contentType, FormPart part) {
        ValidationErrors errors = new ValidationErrors();

        // Binary file uploads (type: string, format: binary/byte) are opaque, only their presence is checked.
        if (isBinaryStringSchema(propertySchema))
            return errors;

        if (isJson(contentType)) {
            try {
                return errors.add(new SchemaValidator(api, propertySchema).validate(ctx, new JsonBody(new String(part.content, UTF_8))));
            } catch (Exception e) {
                return errors.add(ctx.statusCode(400), "Part '%s' is declared as %s but its content could not be parsed as JSON.".formatted(part.name, contentType));
            }
        }

        // Other part content types are currently not validated against the schema.
        return errors;
    }

    @SuppressWarnings("rawtypes")
    private String contentTypeOf(String name, FormPart part, Schema propertySchema, Map<String, Encoding> encodings) {
        if (part.contentType != null)
            return part.contentType;
        if (encodings != null) {
            Encoding encoding = encodings.get(name);
            if (encoding != null && encoding.getContentType() != null)
                return encoding.getContentType();
        }
        return defaultContentType(propertySchema);
    }

    @SuppressWarnings("rawtypes")
    private String defaultContentType(Schema schema) {
        if (isBinaryStringSchema(schema))
            return APPLICATION_OCTET_STREAM;
        if (isObjectOrArray(schema))
            return APPLICATION_JSON;
        return TEXT_PLAIN;
    }

    @SuppressWarnings("rawtypes")
    private boolean isBinaryStringSchema(Schema schema) {
        if (!isStringSchema(schema))
            return false;
        String format = schema.getFormat();
        return "binary".equals(format) || "byte".equals(format);
    }

    @SuppressWarnings("rawtypes")
    private boolean isStringSchema(Schema schema) {
        if ("string".equals(schema.getType()))
            return true;
        return schema.getTypes() != null && schema.getTypes().contains("string");
    }

    @SuppressWarnings("rawtypes")
    private boolean isObjectOrArray(Schema schema) {
        String type = schema.getType();
        if ("object".equals(type) || "array".equals(type))
            return true;
        if (schema.get$ref() != null)
            return true;
        return schema.getProperties() != null;
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

            String contentDisposition = header.getFirstValue("Content-Disposition");
            if (contentDisposition != null) {
                ParameterParser parser = new ParameterParser();
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
