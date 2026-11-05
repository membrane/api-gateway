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

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.prettifier.*;
import com.predic8.membrane.core.util.*;

import java.nio.charset.*;
import java.util.*;

import static com.predic8.membrane.core.http.MimeType.*;

/**
 * Text representations of recorded messages: pretty-printed bodies and curl command lines.
 */
final class MessageText {

    record FormattedBody(String format, String text, boolean truncated, long originalLength) {
    }

    private static final Set<String> CURL_SKIPPED_HEADERS = Set.of("content-length", "host", "transfer-encoding", "connection");

    private MessageText() {
    }

    static String format(String contentType) {
        if (contentType == null || contentType.isBlank())
            return "text";
        final var ct = contentType.toLowerCase(Locale.ROOT);
        if (isJson(ct))
            return "json";
        if (isXML(ct) || isHtml(ct))
            return "xml";
        if (ct.contains("yaml"))
            return "yaml";
        if (isWWWFormUrlEncoded(ct))
            return "form";
        if (isText(ct) || ct.startsWith("text/"))
            return "text";
        return "binary";
    }

    static FormattedBody formatted(Message msg, String contentType, int maxBytes) throws Exception {
        if (msg == null || msg.isBodyEmpty())
            return new FormattedBody("empty", "", false, 0);
        final var bytes = msg.getBodyAsStreamDecoded().readAllBytes();
        final var format = format(contentType);
        if ("binary".equals(format))
            return new FormattedBody(format, "", false, bytes.length);
        final var truncated = bytes.length > maxBytes;
        final var shown = truncated ? Arrays.copyOf(bytes, maxBytes) : bytes;
        final var charset = msg.getCharsetOrDefault();
        return new FormattedBody(format, pretty(format, contentType, shown, charset, truncated), truncated, bytes.length);
    }

    private static String pretty(String format, String contentType, byte[] bytes, Charset charset, boolean truncated) {
        if ("form".equals(format))
            return formLines(new String(bytes, charset));
        if (truncated || "yaml".equals(format) || "text".equals(format))
            return new String(bytes, charset);
        try {
            return new String(Prettifier.getInstance(contentType).prettify(bytes, charset), charset);
        } catch (Exception e) {
            return new String(bytes, charset);
        }
    }

    private static String formLines(String body) {
        final var sb = new StringBuilder();
        for (final var e : URLParamUtil.parseQueryString(body).entrySet())
            for (final var v : e.getValue())
                sb.append(e.getKey()).append(" = ").append(v == null ? "" : v).append('\n');
        return sb.toString();
    }

    static String raw(Message msg) throws Exception {
        return msg.getStartLine() + msg.getHeader().toString() + "\r\n" + (msg.isBodyEmpty() ? "" : msg.getBodyAsStringDecoded());
    }

    /**
     * @param backend whether to address the backend the request was sent to instead of the gateway.
     */
    static String curl(AbstractExchange exc, boolean backend) throws Exception {
        final var req = exc.getRequest();
        final var sb = new StringBuilder("curl");
        if (!"GET".equals(req.getMethod()))
            sb.append(" -X ").append(quote(req.getMethod()));
        sb.append(' ').append(quote(url(exc, backend)));
        for (final var field : req.getHeader().getAllHeaderFields()) {
            if (CURL_SKIPPED_HEADERS.contains(field.getHeaderName().toString().toLowerCase(Locale.ROOT)))
                continue;
            sb.append(" \\\n  -H ").append(quote(field.getHeaderName() + ": " + field.getValue()));
        }
        if (!req.isBodyEmpty()) {
            if ("binary".equals(format(exc.getRequestContentType())))
                sb.append(" \\\n  --data-binary @request-body.bin");
            else
                sb.append(" \\\n  --data-binary ").append(quote(req.getBodyAsStringDecoded()));
        }
        return sb.append('\n').toString();
    }

    private static String url(AbstractExchange exc, boolean backend) {
        final var req = exc.getRequest();
        if (backend) {
            final var destinations = exc.getDestinations();
            if (destinations != null && !destinations.isEmpty() && destinations.getFirst() != null
                && destinations.getFirst().matches("(?i)https?://.*"))
                return destinations.getFirst();
            if (exc.getServer() != null)
                return "http://" + exc.getServer() + req.getUri();
        }
        final var host = req.getHeader().getHost();
        return (host == null ? "http://localhost" : "http://" + host) + req.getUri();
    }

    /**
     * Quotes a value for POSIX shells.
     */
    static String quote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }
}
