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

import com.fasterxml.jackson.databind.node.*;
import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;

import java.util.*;

import static com.predic8.membrane.core.interceptor.administration.v1.ApiJson.*;

/**
 * Renders recorded exchanges as HTTP Archive (HAR 1.2), the format browsers' developer tools import.
 */
final class HarExport {

    private HarExport() {
    }

    static ObjectNode har(List<ObjectNode> entries) {
        final var har = MAPPER.createObjectNode();
        final var log = har.putObject("log");
        log.put("version", "1.2");
        final var creator = log.putObject("creator");
        creator.put("name", "Membrane API Gateway");
        creator.put("version", Constants.VERSION);
        final var array = log.putArray("entries");
        entries.stream()
                .sorted(Comparator.comparing(e -> e.path("startedDateTime").asText()))
                .forEach(array::add);
        return har;
    }

    static ObjectNode entry(String instance, AbstractExchange exc) throws Exception {
        final var entry = MAPPER.createObjectNode();
        entry.put("_instance", instance);
        entry.put("_id", exc.getId());
        entry.put("startedDateTime", isoTime(exc.getTime().getTimeInMillis()));
        final var duration = ExchangeFilter.duration(exc);
        entry.put("time", duration);
        entry.set("request", request(exc));
        entry.set("response", response(exc));
        entry.putObject("cache");
        final var timings = entry.putObject("timings");
        timings.put("send", 0);
        timings.put("wait", duration);
        timings.put("receive", 0);
        return entry;
    }

    private static ObjectNode request(AbstractExchange exc) throws Exception {
        final var req = exc.getRequest();
        final var node = MAPPER.createObjectNode();
        node.put("method", req.getMethod());
        final var host = req.getHeader().getHost();
        node.put("url", "%s://%s%s".formatted(MessageText.inboundScheme(exc), host == null ? "localhost" : host, req.getUri()));
        node.put("httpVersion", "HTTP/" + req.getVersion());
        node.putArray("cookies");
        node.set("headers", headers(req));
        final var query = node.putArray("queryString");
        final var q = req.getUri().indexOf('?');
        if (q >= 0) {
            for (final var p : req.getUri().substring(q + 1).split("&")) {
                if (p.isEmpty())
                    continue;
                final var eq = p.indexOf('=');
                final var param = query.addObject();
                param.put("name", eq < 0 ? p : p.substring(0, eq));
                param.put("value", eq < 0 ? "" : p.substring(eq + 1));
            }
        }
        node.put("headersSize", -1);
        if (req.isBodyEmpty()) {
            node.put("bodySize", 0);
        } else {
            final var body = req.getBodyAsStreamDecoded().readAllBytes();
            node.put("bodySize", body.length);
            final var postData = node.putObject("postData");
            postData.put("mimeType", nonNull(exc.getRequestContentType()));
            postData.put("text", text(body, exc.getRequestContentType(), postData, req));
        }
        return node;
    }

    private static ObjectNode response(AbstractExchange exc) throws Exception {
        final var res = exc.getResponse();
        final var node = MAPPER.createObjectNode();
        if (res == null) {
            // HAR requires a response; status 0 marks a missing one, as browsers do for aborted requests
            node.put("status", 0);
            node.put("statusText", "");
            node.put("httpVersion", "");
            node.putArray("cookies");
            node.putArray("headers");
            final var content = node.putObject("content");
            content.put("size", 0);
            content.put("mimeType", "");
            node.put("redirectURL", "");
            node.put("headersSize", -1);
            node.put("bodySize", -1);
            return node;
        }
        node.put("status", res.getStatusCode());
        node.put("statusText", nonNull(res.getStatusMessage()));
        node.put("httpVersion", "HTTP/" + res.getVersion());
        node.putArray("cookies");
        node.set("headers", headers(res));
        final var content = node.putObject("content");
        final var body = res.isBodyEmpty() ? new byte[0] : res.getBodyAsStreamDecoded().readAllBytes();
        content.put("size", body.length);
        content.put("mimeType", nonNull(exc.getResponseContentType()));
        if (body.length > 0)
            content.put("text", text(body, exc.getResponseContentType(), content, res));
        node.put("redirectURL", nonNull(res.getHeader().getFirstValue("Location")));
        node.put("headersSize", -1);
        node.put("bodySize", body.length);
        return node;
    }

    /**
     * Text content as is, binary content base64 encoded (with {@code encoding: base64}, as HAR specifies).
     */
    private static String text(byte[] body, String contentType, ObjectNode holder, Message msg) {
        if ("binary".equals(MessageText.format(contentType))) {
            holder.put("encoding", "base64");
            return Base64.getEncoder().encodeToString(body);
        }
        return new String(body, msg.getCharsetOrDefault());
    }

    private static ArrayNode headers(Message msg) {
        final var array = MAPPER.createArrayNode();
        for (final var f : msg.getHeader().getAllHeaderFields()) {
            final var h = array.addObject();
            h.put("name", f.getHeaderName().toString());
            h.put("value", f.getValue());
        }
        return array;
    }

    private static String nonNull(String s) {
        return s == null ? "" : s;
    }
}
