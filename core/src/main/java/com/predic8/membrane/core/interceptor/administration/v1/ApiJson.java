/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.administration.v1;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.predic8.membrane.core.http.*;

import java.time.*;
import java.util.*;

import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.http.MimeType.*;
import static java.nio.charset.StandardCharsets.*;

/**
 * JSON and problem responses of the admin API.
 */
final class ApiJson {

    static final ObjectMapper MAPPER = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private ApiJson() {
    }

    static Response ok(Object body) {
        return json(200, body);
    }

    static Response created(Object body) {
        return json(201, body);
    }

    static Response noContent() {
        return Response.noContent().build();
    }

    static Response json(int status, Object body) {
        return Response.statusCode(status)
                .header(CONTENT_TYPE, APPLICATION_JSON_UTF8)
                .body(write(body))
                .build();
    }

    static Response problem(ApiException e) {
        final var body = new LinkedHashMap<String, Object>();
        body.put("type", "https://membrane-api.io/problems/admin");
        body.put("title", e.getTitle());
        body.put("status", e.getStatus());
        if (e.getMessage() != null)
            body.put("detail", e.getMessage());
        return Response.statusCode(e.getStatus())
                .header(CONTENT_TYPE, APPLICATION_PROBLEM_JSON)
                .body(write(body))
                .build();
    }

    static Map<String, Object> readObject(Message message) {
        if (message.isBodyEmpty())
            return Map.of();
        try {
            final var node = MAPPER.readTree(message.getBodyAsStreamDecoded());
            if (node == null || !node.isObject())
                throw ApiException.badRequest("The request body must be a JSON object.");
            //noinspection unchecked
            return MAPPER.convertValue(node, Map.class);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw ApiException.badRequest("The request body is not valid JSON.");
        }
    }

    static String isoTime(long epochMillis) {
        return epochMillis <= 0 ? null : Instant.ofEpochMilli(epochMillis).toString();
    }

    private static byte[] write(Object body) {
        try {
            return MAPPER.writeValueAsString(body).getBytes(UTF_8);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize admin API response.", e);
        }
    }
}
