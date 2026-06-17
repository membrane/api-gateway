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
import com.predic8.membrane.core.openapi.util.SchemaUtil;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Encoding;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import org.apache.commons.fileupload.MultipartStream;
import org.apache.commons.fileupload.ParameterParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;

import static com.predic8.membrane.core.http.Header.CONTENT_TRANSFER_ENCODING;
import static com.predic8.membrane.core.http.MimeType.*;
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

        var schema = mediaType.getSchema();
        if (schema == null) {
            // Without a schema there is nothing to validate the parts against.
            return err;
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
            if (part.name != null)
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
        Map<String, Schema> properties = schema.getProperties();
        if (properties == null)
            return err;

        for (var entry : parts.entrySet()) {
            var name = entry.getKey();

            var propertySchema = properties.get(name);
            if (propertySchema == null) {
                if (FALSE.equals(schema.getAdditionalProperties()))
                    err.add(ctx.statusCode(400), "The multipart/form-data body contains an unexpected property '%s'.".formatted(name));
                continue;
            }

            // For an array property each occurrence of the field name is one array item, so each
            // part is validated against the items schema rather than the array schema itself.
            Schema partSchema = propertySchema;
            if (SchemaUtil.isArray(propertySchema)) {
                Schema items = SchemaUtil.resolveRef(api, propertySchema.getItems());
                if (items != null)
                    partSchema = items;
            }

            // A field name may occur multiple times (array items / multi-file fields), so validate every occurrence.
            for (var part : entry.getValue()) {
                err.add(validatePart(ctx.addJSONpointerSegment(name), partSchema,
                        contentTypeOf(name, part, partSchema, encodings), part));
            }
        }
        return err;
    }

    @SuppressWarnings("rawtypes")
    private ValidationErrors validatePart(ValidationContext ctx, Schema propertySchema, String contentType, FormPart part) {
        var err = new ValidationErrors();

        // Binary file uploads (type: string, format: binary/byte) are opaque, only their presence is checked.
        if (SchemaUtil.isBinaryString(propertySchema))
            return err;

        if (isJson(contentType)) {
            try {
                return err.add(new SchemaValidator(api, propertySchema).validate(ctx, new JsonBody(new String(part.content, UTF_8))));
            } catch (Exception e) {
                return err.add(ctx.statusCode(400), "Part '%s' is declared as %s but its content could not be parsed as JSON.".formatted(part.name, contentType));
            }
        }

        // Scalar parts (e.g. text/plain, the default for primitives) are validated by parsing the
        // raw text into the declared type and validating that value against the schema.
        if (SchemaUtil.isScalar(propertySchema)) {
            return err.add(new SchemaValidator(api, propertySchema)
                    .validate(ctx, SchemaUtil.parseScalar(new String(part.content, UTF_8), propertySchema)));
        }

        // Opaque content (e.g. octet-stream for a complex type) is not validated against the schema.
        return err;
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
        if (SchemaUtil.isBinaryString(schema))
            return APPLICATION_OCTET_STREAM;
        if (SchemaUtil.isObjectOrArray(schema))
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
