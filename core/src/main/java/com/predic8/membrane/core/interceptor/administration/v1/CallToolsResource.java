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
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.transport.http.HttpClient;
import org.slf4j.*;

import java.io.*;
import java.security.*;
import java.util.*;
import java.util.regex.*;

import static com.predic8.membrane.core.interceptor.administration.v1.ApiException.*;
import static com.predic8.membrane.core.interceptor.administration.v1.ApiJson.*;
import static com.predic8.membrane.core.util.text.TextUtil.*;
import static java.nio.charset.StandardCharsets.*;

/**
 * Tools for recorded calls: HAR export, comparing two messages and replaying a request.
 */
final class CallToolsResource {

    private static final Logger log = LoggerFactory.getLogger(CallToolsResource.class);

    static final int MAX_EXPORT = 1000;
    private static final int MAX_DIFF_BYTES = 1_048_576;

    private static final Pattern TOKEN = Pattern.compile("[!#$%&'*+.^_`|~0-9A-Za-z-]+");
    private static final Pattern PATH = Pattern.compile("/[^\\s]*");
    private static final Set<String> HOP_BY_HOP = Set.of("content-length", "transfer-encoding", "connection",
            "keep-alive", "upgrade", "te", "trailer", "proxy-connection");

    /**
     * {@code instance/id} of an exchange, or {@code instance/id/request|response} of a message.
     */
    record Ref(String instance, long id, String message) {

        static Ref exchange(String s) {
            final var slash = s.lastIndexOf('/');
            if (slash < 1)
                throw badRequest("'%s' is not of the form instance/id.".formatted(s));
            return new Ref(s.substring(0, slash), id(s.substring(slash + 1)), null);
        }

        static Ref message(String s) {
            final var slash = s.lastIndexOf('/');
            if (slash < 1)
                throw badRequest("'%s' is not of the form instance/id/request or instance/id/response.".formatted(s));
            final var message = s.substring(slash + 1);
            if (!message.equals("request") && !message.equals("response"))
                throw badRequest("'%s' must end with /request or /response.".formatted(s));
            final var e = exchange(s.substring(0, slash));
            return new Ref(e.instance(), e.id(), message);
        }

        private static long id(String s) {
            try {
                return Long.parseLong(s);
            } catch (NumberFormatException e) {
                throw badRequest("'%s' is not an exchange id.".formatted(s));
            }
        }

        @Override
        public String toString() {
            return instance + "/" + id + (message == null ? "" : "/" + message);
        }
    }

    /**
     * One side of a comparison.
     */
    /**
     * @param truncated whether {@code text} is only the beginning of the body
     * @param sha256    hash of the decoded body, for binary bodies of this instance; null otherwise
     */
    record Side(String startLine, List<ExchangesResource.HeaderEntry> headers, String format, String text,
                boolean truncated, String sha256) {
    }

    record HeaderDiff(String name, String left, String right) {
    }

    /**
     * @param truncated whether a body was longer than the compared text
     */
    record BodyDiff(String format, boolean identical, boolean truncated, String unifiedDiff) {
    }

    record StartLines(String left, String right) {
    }

    record Diff(String left, String right, StartLines startLine, List<HeaderDiff> headers, BodyDiff body) {
    }

    record ReplayResult(String target, String url, int statusCode, String statusMessage, long durationMillis,
                        List<ExchangesResource.HeaderEntry> headers, MessageText.FormattedBody body) {
    }

    private final ConsoleContext ctx;
    private final ExchangesResource exchanges;
    private final HttpClient httpClient;

    CallToolsResource(ConsoleContext ctx, ExchangesResource exchanges) {
        this.ctx = ctx;
        this.exchanges = exchanges;
        this.httpClient = ctx.router().getHttpClientFactory().createClient(ctx.router().getHttpClientConfig());
    }

    // ------------------------------------------------------------------ HAR

    Response export(ApiRequest req) throws Exception {
        return harResponse(HarExport.har(localEntries(exportRefs(req))));
    }

    static List<Ref> exportRefs(ApiRequest req) {
        final var ids = req.repeated("id");
        if (ids.isEmpty())
            throw badRequest("Query parameter 'id' is required.");
        if (ids.size() > MAX_EXPORT)
            throw badRequest("At most %d exchanges can be exported at once.".formatted(MAX_EXPORT));
        return ids.stream().map(Ref::exchange).toList();
    }

    /**
     * HAR entries of the exchanges of this instance among {@code refs}.
     */
    List<ObjectNode> localEntries(List<Ref> refs) throws Exception {
        final var entries = new ArrayList<ObjectNode>();
        for (final var ref : refs) {
            final var exc = exchanges.exchange(ref.instance(), ref.id());
            if (exc.getRequest() != null)
                entries.add(HarExport.entry(ref.instance(), exc));
        }
        return entries;
    }

    static Response harResponse(ObjectNode har) {
        final var res = ok(har);
        res.getHeader().setValue(Header.CONTENT_DISPOSITION, "attachment; filename=\"membrane-calls.har\"");
        return res;
    }

    // ------------------------------------------------------------------ diff

    Response diff(ApiRequest req) throws Exception {
        final var left = Ref.message(req.param("left").orElseThrow(() -> badRequest("Query parameter 'left' is required.")));
        final var right = Ref.message(req.param("right").orElseThrow(() -> badRequest("Query parameter 'right' is required.")));
        return ok(diff(left, localSide(left), right, localSide(right)));
    }

    Side localSide(Ref ref) throws Exception {
        final var exc = exchanges.exchange(ref.instance(), ref.id());
        final var msg = ExchangesResource.message(exc, ref.message());
        final var contentType = "response".equals(ref.message()) ? exc.getResponseContentType() : exc.getRequestContentType();
        final var body = MessageText.formatted(msg, contentType, MAX_DIFF_BYTES);
        final var headers = Arrays.stream(msg.getHeader().getAllHeaderFields())
                .map(f -> new ExchangesResource.HeaderEntry(f.getHeaderName().toString(), f.getValue()))
                .toList();
        return new Side(msg.getStartLine().trim(), headers, body.format(), body.text(), body.truncated(),
                "binary".equals(body.format()) ? sha256(msg) : null);
    }

    private static String sha256(Message msg) throws Exception {
        final var digest = MessageDigest.getInstance("SHA-256");
        try (final var in = new DigestInputStream(msg.getBodyAsStreamDecoded(), digest)) {
            in.transferTo(OutputStream.nullOutputStream());
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    static Diff diff(Ref left, Side l, Ref right, Side r) {
        final var lh = headerMap(l.headers());
        final var rh = headerMap(r.headers());
        final var names = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
        names.addAll(lh.keySet());
        names.addAll(rh.keySet());
        final var headers = new ArrayList<HeaderDiff>();
        for (final var name : names) {
            final var a = lh.get(name);
            final var b = rh.get(name);
            if (!Objects.equals(a, b))
                headers.add(new HeaderDiff(name, a, b));
        }
        final var unified = TextDiff.unified(l.text(), r.text(), left.toString(), right.toString());
        final var format = Objects.equals(l.format(), r.format()) ? l.format() : l.format() + "/" + r.format();
        final var truncated = l.truncated() || r.truncated();
        return new Diff(left.toString(), right.toString(), new StartLines(l.startLine(), r.startLine()), headers,
                new BodyDiff(format, identical(l, r, unified, truncated), truncated, unified));
    }

    /**
     * Binary bodies have no text, so they are compared by their hashes; a missing hash never counts as equal. Text
     * bodies are equal only if no text was cut off.
     */
    private static boolean identical(Side l, Side r, String unified, boolean truncated) {
        if ("binary".equals(l.format()) || "binary".equals(r.format()))
            return "binary".equals(l.format()) && "binary".equals(r.format())
                   && l.sha256() != null && l.sha256().equals(r.sha256());
        return !truncated && unified.isEmpty();
    }

    /**
     * Header values by case-insensitive name; repeated fields are joined with ", ".
     */
    private static Map<String, String> headerMap(List<ExchangesResource.HeaderEntry> headers) {
        final var map = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
        for (final var h : headers)
            map.merge(h.name(), h.value(), (a, b) -> a + ", " + b);
        return map;
    }

    // ------------------------------------------------------------------ replay

    /**
     * Sends the recorded request again, optionally changed, through the gateway or straight to the recorded backend.
     * Only the method, path, header fields and body can be changed, not the host the request goes to.
     */
    Response replay(ApiRequest req) throws Exception {
        final var exc = exchanges.exchange(req.path(0), req.pathLong(1));
        final var recorded = exc.getRequest();
        if (recorded == null)
            throw notFound("The exchange has no request.");
        final var body = ApiJson.readObject(req.exchange().getRequest());

        final var target = stringOr(body, "target", "gateway");
        if (!target.equals("gateway") && !target.equals("backend"))
            throw badRequest("Property 'target' must be 'gateway' or 'backend'.");
        final var method = stringOr(body, "method", recorded.getMethod());
        if (!TOKEN.matcher(method).matches())
            throw badRequest("Property 'method' is not a valid HTTP method.");
        final var path = stringOr(body, "path", recorded.getUri());
        if (!PATH.matcher(path).matches())
            throw badRequest("Property 'path' must start with / and must not contain white space.");

        final var builder = new Request.Builder().method(method).url(ctx.router().getConfiguration().getUriFactory(), path);
        for (final var h : headers(body, recorded))
            builder.header(h.name(), h.value());
        final byte[] content = body.containsKey("body")
                ? stringOr(body, "body", "").getBytes(UTF_8)
                : recorded.isBodyEmpty() ? new byte[0] : recorded.getBodyAsStreamDecoded().readAllBytes();
        if (content.length > 0)
            builder.body(content);
        final var replay = new Exchange(null);
        replay.setRequest(builder.build());
        if (body.containsKey("headers") || content.length > 0)
            replay.getRequest().getHeader().removeFields(Header.CONTENT_ENCODING);

        final var destination = destination(exc, target, path);
        replay.setDestinations(new ArrayList<>(List.of(destination)));
        log.info("Replaying exchange {} of instance {} to {}", exc.getId(), escapeControlCharsForLog(ctx.instanceName()),
                escapeControlCharsForLog(destination));

        final var start = System.currentTimeMillis();
        try {
            httpClient.call(replay);
        } catch (Exception e) {
            throw new ApiException(502, "Bad gateway.", "The replayed request failed: %s".formatted(e.getMessage()));
        }
        final var res = replay.getResponse();
        final var headers = Arrays.stream(res.getHeader().getAllHeaderFields())
                .map(f -> new ExchangesResource.HeaderEntry(f.getHeaderName().toString(), f.getValue()))
                .toList();
        return ok(new ReplayResult(target, destination, res.getStatusCode(), res.getStatusMessage(),
                System.currentTimeMillis() - start, headers,
                MessageText.formatted(res, res.getHeader().getContentType(), 1_048_576)));
    }

    private static List<ExchangesResource.HeaderEntry> headers(Map<String, Object> body, Request recorded) {
        final var result = new ArrayList<ExchangesResource.HeaderEntry>();
        if (!body.containsKey("headers")) {
            for (final var f : recorded.getHeader().getAllHeaderFields())
                if (!HOP_BY_HOP.contains(f.getHeaderName().toString().toLowerCase(Locale.ROOT)))
                    result.add(new ExchangesResource.HeaderEntry(f.getHeaderName().toString(), f.getValue()));
            return result;
        }
        if (!(body.get("headers") instanceof List<?> list))
            throw badRequest("Property 'headers' must be an array.");
        for (final var item : list) {
            if (!(item instanceof Map<?, ?> m) || !(m.get("name") instanceof String name) || !(m.get("value") instanceof String value))
                throw badRequest("Every header needs a string 'name' and 'value'.");
            if (!TOKEN.matcher(name).matches() || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0)
                throw badRequest("Header '%s' is not valid.".formatted(name));
            if (!HOP_BY_HOP.contains(name.toLowerCase(Locale.ROOT)))
                result.add(new ExchangesResource.HeaderEntry(name, value));
        }
        return result;
    }

    private static String destination(AbstractExchange exc, String target, String path) {
        if (target.equals("backend")) {
            final var destinations = exc.getDestinations();
            if (destinations == null || destinations.isEmpty() || destinations.getFirst() == null
                || !destinations.getFirst().matches("(?i)https?://.*"))
                throw badRequest("The backend of this exchange is not known.");
            final var recorded = destinations.getFirst();
            final var schemeEnd = recorded.indexOf("://") + 3;
            final var pathStart = recorded.indexOf('/', schemeEnd);
            return (pathStart < 0 ? recorded : recorded.substring(0, pathStart)) + path;
        }
        final var proxy = exc.getProxy();
        if (proxy == null || proxy.getKey().getPort() <= 0)
            throw badRequest("The exchange was not received on a port and cannot be replayed through the gateway.");
        final var ip = proxy.getKey().getIp();
        return "%s://%s:%d%s".formatted(MessageText.inboundScheme(exc), ip == null || ip.isBlank() ? "localhost" : ip,
                proxy.getKey().getPort(), path);
    }

    private static String stringOr(Map<String, Object> body, String name, String defaultValue) {
        final var value = body.get(name);
        if (value == null)
            return defaultValue;
        if (!(value instanceof String s))
            throw badRequest("Property '%s' must be a string.".formatted(name));
        return s;
    }
}
