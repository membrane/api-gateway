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

package com.predic8.membrane.core.interceptor.sqlinjection;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.util.URI;
import com.predic8.membrane.core.util.URIFactory;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static com.predic8.membrane.core.http.MimeType.isWWWFormUrlEncoded;
import static com.predic8.membrane.core.util.URLParamUtil.parseQueryString;

/**
 * Inspects an HTTP {@link Message} (request or response) for SQL injection signatures.
 * <p>
 * Detection engine: given a rule set it
 * scans the path, query parameters, body (JSON / form / raw text) and optionally the headers of a message and
 * reports the first rule violated. {@link SqlInjectionProtectionInterceptor} owns configuration, lifecycle and
 * what to do with a {@link Detection};
 */
public class SqlInjectionProtection {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final SqlInjectionRuleSet ruleSet;
    private final boolean inspectHeaders;
    private final URIFactory uriFactory;

    public SqlInjectionProtection(SqlInjectionRuleSet ruleSet, boolean inspectHeaders, URIFactory uriFactory) {
        this.ruleSet = ruleSet;
        this.inspectHeaders = inspectHeaders;
        this.uriFactory = uriFactory;
    }

    /**
     * @return the first SQL injection rule the message violates, or empty if it looks clean.
     */
    public Optional<Detection> scan(Message message) throws IOException, URISyntaxException {
        Optional<Detection> hit;

        // Path and query parameters only exist on requests.
        if (message instanceof Request request) {
            URI uri = uriFactory.create(request.getUri());

            hit = inspect("path", uri.getPath());
            if (hit.isPresent()) return hit;

            if (uri.getRawQuery() != null) {
                hit = inspectParams("query", uri.getRawQuery());
                if (hit.isPresent()) return hit;
            }
        }

        if (!message.isBodyEmpty()) {
            String contentType = message.getHeader().getContentType();
            if (message.isJSON()) {
                hit = inspectJsonOrText(message.getBodyAsStringDecoded());
                if (hit.isPresent()) return hit;
            } else if (isWWWFormUrlEncoded(contentType)) {
                hit = inspectParams("form", message.getBodyAsStringDecoded());
                if (hit.isPresent()) return hit;
            } else if (!message.isBinary() && !message.isImage()) {
                // Raw text bodies (text/plain, XML, GraphQL, ...): scan the whole body.
                hit = inspect("body", message.getBodyAsStringDecoded());
                if (hit.isPresent()) return hit;
            }
        }

        // Headers (opt-in: higher false-positive risk)
        if (inspectHeaders) {
            for (var f : message.getHeader().getAllHeaderFields()) {
                hit = inspect("header " + f.getHeaderName(), f.getValue());
                if (hit.isPresent()) return hit;
            }
        }

        return Optional.empty();
    }

    private Optional<Detection> inspectParams(String location, String queryString) {
        return parseQueryString(queryString).entrySet().stream()
                .flatMap(e -> Stream.concat(Stream.of(e.getKey()), e.getValue().stream()))
                .map(v -> inspect(location, v))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
    }

    /**
     * Inspects a body whose Content-Type claims JSON. If the body does not actually parse as JSON
     * ({@link Message#isJSON()} only inspects the header), fall back to scanning it as raw text so a
     * malformed body never breaks inspection.
     */
    private Optional<Detection> inspectJsonOrText(String body) {
        try {
            return inspectJson(body);
        } catch (IOException e) {
            return inspect("body", body);
        }
    }

    private Optional<Detection> inspectJson(String body) throws IOException {
        List<Detection> result = new ArrayList<>(1);
        walkJson(MAPPER.readTree(body), result::add);
        return result.stream().findFirst();
    }

    private void walkJson(JsonNode node, Consumer<Detection> sink) {
        if (node.isObject()) {
            node.fields().forEachRemaining(e -> {
                inspect("json field", e.getKey()).ifPresent(sink);
                walkJson(e.getValue(), sink);
            });
        } else if (node.isArray()) {
            node.forEach(child -> walkJson(child, sink));
        } else if (node.isTextual()) {
            inspect("json value", node.asText()).ifPresent(sink);
        }
    }

    private Optional<Detection> inspect(String location, String value) {
        return ruleSet.firstMatch(value).map(rule -> new Detection(location, rule));
    }

    /**
     * Where in the message a rule matched (e.g. "query", "json value") and which rule it was.
     */
    public record Detection(String location, SqlInjectionRule rule) {}
}
