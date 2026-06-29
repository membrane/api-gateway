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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.predic8.membrane.core.openapi.model.Message;
import com.predic8.membrane.core.openapi.util.OpenAPI32Parser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.openapi.validators.ValidationContext.ValidatedEntityType.BODY;
import static com.predic8.membrane.core.openapi.validators.ValidationContext.ValidatedEntityType.MEDIA_TYPE;
import static com.predic8.membrane.core.util.MediaTypeUtil.getMostSpecificMediaType;
import static java.lang.String.format;

public abstract class AbstractBodyValidator {

    protected final OpenAPI api;

    public abstract int getDefaultStatusCode();

    public abstract String getMessageName();

    public AbstractBodyValidator(OpenAPI api) {
        this.api = api;
    }

    private static final ObjectMapper om = new ObjectMapper();

    protected ValidationErrors validateBodyAccordingToMediaType(ValidationContext ctx, String mediaType, MediaType mediaTypeObj, Message<?,?> message) {
        ValidationErrors errors = new ValidationErrors();

        // OpenAPI 3.2 sequential media types (e.g. application/jsonl, text/event-stream): validate
        // each streamed item against itemSchema. Attached to the MediaType by OpenAPI32Parser.
        Schema<?> itemSchema = OpenAPI32Parser.getItemSchema(mediaTypeObj);
        if (itemSchema != null) {
            if (isEventStream(mediaType)) {
                return validateItems(ctx, itemSchema, message, AbstractBodyValidator::parseServerSentEvents);
            }
            if (isJsonSequence(mediaType)) {
                return validateItems(ctx, itemSchema, message, body -> splitJsonItems(mediaType, body));
            }
        }

        // Use the value of the OpenAPI spec for comparison, so it can not
        // be influenced from the outside.
        if ( APPLICATION_JSON_CONTENT_TYPE.match(mediaType)) {
            if (mediaTypeObj.getSchema() != null && mediaTypeObj.getSchema().get$ref() != null) {
                ctx = ctx.schemaType(mediaTypeObj.getSchema().get$ref());
            }
            return errors.add(new SchemaValidator(api, mediaTypeObj.getSchema()).validate(ctx, message.getBody()));
        }
        if(isXML(mediaType)) {
            if (mediaTypeObj.getSchema() != null && mediaTypeObj.getSchema().get$ref() != null) {
                ctx = ctx.schemaType(mediaTypeObj.getSchema().get$ref());
            }
            ctx = ctx.content(ValidationContext.Content.XML);
            return errors.add(new SchemaValidator(api, mediaTypeObj.getSchema()).validate(ctx, message.getBody()));
        }
        if(isWWWFormUrlEncoded(mediaType)) {
            return errors.add(new FormUrlEncodedValidator(api).validate(ctx, mediaTypeObj, message));
        }
        if(isMultipartFormData(mediaType)) {
            return errors.add(new MultipartFormDataValidator(api).validate(ctx.statusCode(getDefaultStatusCode()), mediaTypeObj, message));
        }
        // Other types that can't be validated against OpenAPI are Ok.
        return errors;
    }

    protected ValidationErrors validateContentTypeHeader(ValidationContext ctx, Message<?,?> message)  {
        ValidationErrors errors = new ValidationErrors();
        if (message.getMediaType() == null) {
            errors.add(ctx.statusCode(getDefaultStatusCode()), getMessageName() + " has a body, but no Content-Type header.");
            return errors;
        }
        if (message.getMediaType().getBaseType().equals("*") || message.getMediaType().getSubType().equals("*")) {
            errors.add(ctx.statusCode(getDefaultStatusCode()), "Content-Type %s is not concrete.".formatted(message.getMediaType()));
        }
        return errors;
    }

    protected ValidationErrors validateMediaTypeForMessageType(ValidationContext ctx, String mediaType, MediaType mediaTypeObj, Message<?,?> response) {
        ValidationErrors errors = new ValidationErrors();
        // Check if the mediaType of the message is the same as the one declared for that status code
        // in the OpenAPI document.
        if (!response.isOfMediaType(mediaType)) {
            errors.add(ctx.entityType(MEDIA_TYPE)
                            .entity(response.getMediaType().toString()),
                    format("Message has media type %s instead of the expected type %s.", response.getMediaType(), mediaType));
            return errors;
        }
        errors.add(validateBodyAccordingToMediaType(ctx.statusCode(getDefaultStatusCode()), mediaType, mediaTypeObj, response));
        return errors;
    }

    protected ValidationErrors validateBodyInternal(ValidationContext ctx, Message<?,?> msg, Content content) {
        if (content == null)
            return null;

        ValidationErrors errors = validateContentTypeHeader(ctx.entityType(BODY), msg);
        if (!errors.isEmpty())
            return errors;

        String mostSpecificMediaType;
        try {
            mostSpecificMediaType = getMostSpecificMediaType(msg.getMediaType().toString(), content.keySet()).orElseThrow();
        } catch (Exception e) {
            return ValidationErrors.error(ctx.statusCode(getStatusCodeForWrongMediaType()).entityType(MEDIA_TYPE).entity(msg.getMediaType().toString()),  "The media type(Content-Type header) of the %s does not match any of %s.".formatted(getMessageName(), content.keySet()));
        }

        return validateMediaTypeForMessageType(ctx.statusCode(getStatusCodeForWrongMediaType()), mostSpecificMediaType, content.get(mostSpecificMediaType), msg);
    }

    /**
     * If the media type is not supported the status code should be different for requests and responses
     */
    protected abstract int getStatusCodeForWrongMediaType();

    /**
     * True for JSON based sequential media types whose items are each a single JSON value
     * (JSON Lines, ND-JSON and JSON Text Sequences).
     */
    private static boolean isJsonSequence(String mediaType) {
        String subType = mediaType.toLowerCase();
        return subType.contains("jsonl") || subType.contains("json-seq")
               || subType.contains("x-ndjson") || subType.contains("ndjson");
    }

    /** True for {@code text/event-stream} (Server-Sent Events). */
    private static boolean isEventStream(String mediaType) {
        return mediaType.toLowerCase().contains("event-stream");
    }

    /** One item of a sequential body: either a parsed node, or an extraction error. */
    private record SequentialItem(JsonNode node, String error) {
        static SequentialItem of(JsonNode node) { return new SequentialItem(node, null); }
        static SequentialItem error(String error) { return new SequentialItem(null, error); }
    }

    private ValidationErrors validateItems(ValidationContext ctx, Schema<?> itemSchema, Message<?, ?> message,
                                           Function<String, List<SequentialItem>> extractor) {
        ValidationErrors errors = new ValidationErrors();
        String body;
        try {
            body = message.getBody().asString();
        } catch (IOException e) {
            return errors.add(ctx.statusCode(getDefaultStatusCode()).entityType(BODY),
                    getMessageName() + " body cannot be read.");
        }

        int index = 0;
        for (SequentialItem item : extractor.apply(body)) {
            ValidationContext itemCtx = ctx.addJSONpointerSegment(String.valueOf(index));
            if (item.error() != null) {
                errors.add(itemCtx.statusCode(getDefaultStatusCode()).entityType(BODY),
                        format("Item %d of the sequential body %s", index, item.error()));
            } else {
                errors.add(new SchemaValidator(api, itemSchema).validate(itemCtx, item.node()));
            }
            index++;
        }
        return errors;
    }

    private static List<SequentialItem> splitJsonItems(String mediaType, String body) {
        String separator = mediaType.toLowerCase().contains("json-seq") ? "\u001E" : "\n";
        List<SequentialItem> items = new ArrayList<>();
        for (String raw : body.split(separator)) {
            String chunk = raw.strip();
            if (chunk.isEmpty())
                continue;
            try {
                items.add(SequentialItem.of(om.readTree(chunk)));
            } catch (IOException e) {
                items.add(SequentialItem.error("cannot be parsed as JSON."));
            }
        }
        return items;
    }

    /**
     * Parses a {@code text/event-stream} body into one item per event. Following OpenAPI 3.2, each
     * event is modeled as an object with the SSE fields ({@code data}, {@code event}, {@code id},
     * {@code retry}); {@code retry} is a JSON number, every other field is a string. Comment lines
     * (starting with {@code :}) are ignored and consecutive {@code data} lines are joined with
     * newlines.
     */
    private static List<SequentialItem> parseServerSentEvents(String body) {
        List<SequentialItem> events = new ArrayList<>();
        ObjectNode event = om.createObjectNode();
        StringBuilder data = null;
        boolean started = false;

        for (String line : body.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1)) {
            if (line.isEmpty()) {
                if (started) {
                    events.add(finishEvent(event, data));
                    event = om.createObjectNode();
                    data = null;
                    started = false;
                }
                continue;
            }
            if (line.startsWith(":")) // comment
                continue;

            started = true;
            int colon = line.indexOf(':');
            String field = colon < 0 ? line : line.substring(0, colon);
            String value = colon < 0 ? "" : line.substring(colon + 1);
            if (value.startsWith(" "))
                value = value.substring(1);

            switch (field) {
                case "data" -> {
                    if (data == null) data = new StringBuilder();
                    else data.append('\n');
                    data.append(value);
                }
                case "retry" -> {
                    if (value.matches("\\d+")) event.put("retry", Long.parseLong(value));
                }
                default -> event.put(field, value);
            }
        }
        if (started)
            events.add(finishEvent(event, data));
        return events;
    }

    private static SequentialItem finishEvent(ObjectNode event, StringBuilder data) {
        if (data != null)
            event.put("data", data.toString());
        return SequentialItem.of(event);
    }

}
