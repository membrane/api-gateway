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

import com.fasterxml.jackson.core.type.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.exchangestore.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.administration.*;
import com.predic8.membrane.core.interceptor.rest.*;
import com.predic8.membrane.core.proxies.*;

import java.util.*;

import static com.predic8.membrane.core.exchange.ExchangeState.*;
import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.interceptor.administration.AdminRESTInterceptor.*;
import static com.predic8.membrane.core.interceptor.administration.v1.ApiException.*;
import static com.predic8.membrane.core.interceptor.administration.v1.ApiJson.*;
import static com.predic8.membrane.core.transport.http2.Http2ServerHandler.*;
import static com.predic8.membrane.core.util.ComparatorFactory.*;
import static java.nio.charset.StandardCharsets.*;

/**
 * {@code /exchanges}: the recorded calls of this instance's exchange store.
 */
final class ExchangesResource {

    static final Set<String> SORTS = Set.of("time", "statusCode", "proxy", "method", "path", "client", "server",
            "reqContentType", "reqContentLength", "respContentType", "respContentLength", "duration", "instance");

    record ExchangeSummary(String instance, long id, String time, Integer statusCode, String apiId, String proxy,
                           Integer listenPort, String protocol, String method, String path, String client,
                           String server, Integer serverPort, String reqContentType, Long reqContentLength,
                           String respContentType, Long respContentLength, boolean completed, long duration) {
    }

    record NodeStatus(String node, Integer statusCode, String exception) {
    }

    record ExchangeDetail(String instance, long id, String time, Integer statusCode, String apiId, String proxy,
                          Integer listenPort, String protocol, String method, String path, String client,
                          String server, Integer serverPort, String reqContentType, Long reqContentLength,
                          String respContentType, Long respContentLength, boolean completed, long duration,
                          List<String> destinations, List<NodeStatus> nodeStatus, String error,
                          String requestBodyFormat, String responseBodyFormat) {
    }

    record InstanceResult(String instance, String status, String error) {
    }

    record ExchangePage(long total, int offset, int limit, List<InstanceResult> instances,
                        List<ExchangeSummary> exchanges, Map<String, Long> lastModified) {
    }

    record HeaderEntry(String name, String value) {
    }

    record Headers(String startLine, List<HeaderEntry> headers) {
    }

    record Capabilities(boolean list, boolean sort, boolean waitForModification, boolean bodySearch,
                        boolean headerSearch, boolean timeRange, boolean frames) {
    }

    record StoreInfo(String instance, String type, Long maxBytes, Long usedBytes, Long count, String oldest,
                     Capabilities capabilities) {
    }

    record Stores(List<StoreInfo> stores) {
    }

    record Facets(List<String> method, List<Integer> statusCode, List<String> proxy, List<String> client,
                  List<String> server, List<String> reqContentType, List<String> respContentType,
                  List<String> instance) {
    }

    private static final String WAIT_FOR_MODIFICATION_FORMAT = "Query parameter 'waitForModification' must be the JSON encoded 'lastModified' object of a previous response.";

    private final ConsoleContext ctx;

    ExchangesResource(ConsoleContext ctx) {
        this.ctx = ctx;
    }

    Response list(ApiRequest req) throws Exception {
        final var offset = req.intParam("offset", 0, 0, Integer.MAX_VALUE);
        final var limit = req.intParam("limit", 50, 1, 1000);
        final var sort = req.enumParam("sort", "time", SORTS);
        final var order = req.enumParam("order", "desc", Set.of("asc", "desc"));
        final var filter = ExchangeFilter.of(req, ctx.useXForwardedForAsClientAddr());
        if (ctx.isExcluded(req))
            return ok(new ExchangePage(0, offset, limit, List.of(), List.of(), Map.of()));

        final var store = store();
        if (filter.body() != null && !listsBodies(store))
            throw notImplemented("The exchange store (%s) cannot search bodies.".formatted(storeType(store)));
        waitForModification(req, store);

        final var result = isListedInMemory(store)
                ? queryInMemory(store, filter, sort, order, offset, limit)
                : queryStore(store, filter, sort, order, offset, limit);

        return ok(new ExchangePage(result.getCount(), offset, limit,
                List.of(new InstanceResult(ctx.instanceName(), "ok", null)),
                result.getExchanges().stream().map(this::summary).toList(),
                Map.of(ctx.instanceName(), result.getLastModified())));
    }

    Response facets(ApiRequest req) {
        if (ctx.isExcluded(req))
            return ok(new Facets(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of()));
        final var collector = new PropertyValueCollector();
        collector.setUseXForwardedForAsClientAddr(ctx.useXForwardedForAsClientAddr());
        store().collect(collector);
        return ok(new Facets(sorted(collector.getMethods()), sorted(collector.getStatusCodes()),
                sorted(collector.getProxies()), sorted(collector.getClients()), sorted(collector.getServers()),
                sorted(collector.getReqContentTypes()), sorted(collector.getRespContentTypes()),
                List.of(ctx.instanceName())));
    }

    Response stores(ApiRequest req) {
        if (ctx.isExcluded(req))
            return ok(new Stores(List.of()));
        final var store = store();
        final var type = storeType(store);
        final var inMemory = isListedInMemory(store);
        final var persistent = store instanceof AbstractPersistentExchangeStore;
        final var capabilities = new Capabilities(inMemory || persistent, inMemory || persistent,
                store instanceof LimitedMemoryExchangeStore || store instanceof FileExchangeStore,
                inMemory && listsBodies(store), inMemory, inMemory, false);
        if (store instanceof LimitedMemoryExchangeStore lm) {
            final var oldest = lm.getOldestTimeResSent();
            return ok(new Stores(List.of(new StoreInfo(ctx.instanceName(), type, (long) lm.getMaxSize(),
                    (long) lm.getCurrentSize(), (long) lm.getAllExchangesAsList().size(),
                    oldest == null ? null : isoTime(oldest), capabilities))));
        }
        return ok(new Stores(List.of(new StoreInfo(ctx.instanceName(), type, null, null, null, null, capabilities))));
    }

    Response get(ApiRequest req) {
        final var exc = exchange(req);
        final var s = summary(exc);
        return ok(new ExchangeDetail(s.instance(), s.id(), s.time(), s.statusCode(), s.apiId(), s.proxy(),
                s.listenPort(), s.protocol(), s.method(), s.path(), s.client(), s.server(), s.serverPort(),
                s.reqContentType(), s.reqContentLength(), s.respContentType(), s.respContentLength(), s.completed(),
                s.duration(), exc.getDestinations() == null ? null : List.copyOf(exc.getDestinations()),
                nodeStatus(exc), exc.getErrorMessage(),
                exc.getRequest() == null ? null : MessageText.format(exc.getRequestContentType()),
                exc.getResponse() == null ? null : MessageText.format(exc.getResponseContentType())));
    }

    Response headers(ApiRequest req) {
        final var msg = message(req);
        final var headers = Arrays.stream(msg.getHeader().getAllHeaderFields())
                .map(f -> new HeaderEntry(f.getHeaderName().toString(), f.getValue()))
                .toList();
        return ok(new Headers(msg.getStartLine().trim(), headers));
    }

    Response raw(ApiRequest req) throws Exception {
        return untrustedText(MessageText.raw(message(req)));
    }

    Response body(ApiRequest req) throws Exception {
        final var exc = exchange(req);
        final var msg = message(exc, req.path(2));
        if (msg.isBodyEmpty())
            return Response.noContent().build();
        final var contentType = isResponse(req.path(2)) ? exc.getResponseContentType() : exc.getRequestContentType();
        // The body is attacker-controlled: never let it render as a document on the admin origin.
        final var rb = Response.ok()
                .contentType(contentType == null || contentType.isBlank() ? APPLICATION_OCTET_STREAM : contentType)
                .body(msg.getBodyAsStream(), false)
                .header(CONTENT_DISPOSITION, "attachment")
                .header(X_CONTENT_TYPE_OPTIONS, "nosniff")
                .header(CONTENT_SECURITY_POLICY, "sandbox");
        final var contentEncoding = msg.getHeader().getContentEncoding();
        if (contentEncoding != null)
            rb.header(CONTENT_ENCODING, contentEncoding);
        return rb.build();
    }

    Response formatted(ApiRequest req) throws Exception {
        final var exc = exchange(req);
        final var msg = message(exc, req.path(2));
        final var contentType = isResponse(req.path(2)) ? exc.getResponseContentType() : exc.getRequestContentType();
        return ok(MessageText.formatted(msg, contentType, req.intParam("maxBytes", 1_048_576, 1, 16 * 1_048_576)));
    }

    Response curl(ApiRequest req) throws Exception {
        final var exc = exchange(req);
        if (exc.getRequest() == null)
            throw notFound("The exchange has no request.");
        final var target = req.enumParam("target", "gateway", Set.of("gateway", "backend"));
        return untrustedText(MessageText.curl(exc, "backend".equals(target)));
    }

    private void waitForModification(ApiRequest req, ExchangeStore store) throws InterruptedException {
        final var known = req.param("waitForModification");
        if (known.isEmpty())
            return;
        final var timeout = req.intParam("timeout", 30, 1, 60);
        final Map<String, Long> lastModified;
        try {
            lastModified = ApiJson.MAPPER.readValue(known.get(), new TypeReference<>() {
            });
        } catch (Exception e) {
            throw badRequest(WAIT_FOR_MODIFICATION_FORMAT);
        }
        if (lastModified == null)
            throw badRequest(WAIT_FOR_MODIFICATION_FORMAT);
        final var mine = lastModified.get(ctx.instanceName());
        if (mine != null)
            store.waitForModification(mine, timeout * 1000L);
    }

    private ExchangeQueryResult queryInMemory(ExchangeStore store, ExchangeFilter filter, String sort, String order,
                                              int offset, int limit) {
        final List<AbstractExchange> snapshot;
        // Read the marker first: stores return a fresh copy from getAllExchangesAsList(), and an exchange recorded
        // after the copy must not be covered by the returned marker.
        final var lastModified = store.getLastModified();
        final var all = store.getAllExchangesAsList();
        synchronized (all) {
            snapshot = new ArrayList<>(all);
        }
        final var matching = new ArrayList<AbstractExchange>();
        for (final var exc : snapshot)
            if (filter.test(exc))
                matching.add(exc);
        matching.sort(getAbstractExchangeComparator("instance".equals(sort) ? "time" : sort, order));
        final var page = matching.subList(Math.min(offset, matching.size()), (int) Math.min((long) offset + limit, matching.size()));
        return new ExchangeQueryResult(List.copyOf(page), matching.size(), lastModified);
    }

    /**
     * Persistent stores query their backend; only the filters of the legacy console are passed on.
     */
    private ExchangeQueryResult queryStore(ExchangeStore store, ExchangeFilter filter, String sort, String order,
                                           int offset, int limit) throws Exception {
        if (!(store instanceof AbstractPersistentExchangeStore))
            throw notImplemented("The exchange store (%s) cannot be queried.".formatted(storeType(store)));
        if (filter.needsInMemoryFiltering())
            throw notImplemented("The exchange store (%s) does not support the filters statusCode class, from, to, minDuration, header or body.".formatted(storeType(store)));
        final var params = new HashMap<String, String>();
        putIfNotNull(params, "method", filter.method());
        putIfNotNull(params, "statuscode", filter.statusCode() == null ? null : String.valueOf(filter.statusCode()));
        putIfNotNull(params, "proxy", filter.proxy());
        putIfNotNull(params, "client", filter.client());
        putIfNotNull(params, "server", filter.server());
        putIfNotNull(params, "reqcontenttype", filter.reqContentType());
        putIfNotNull(params, "respcontenttype", filter.respContentType());
        putIfNotNull(params, "search", filter.text());
        params.put("sort", "instance".equals(sort) ? "time" : sort);
        params.put("order", order);
        params.put("offset", String.valueOf(offset));
        params.put("max", String.valueOf(limit));
        return store.getFilteredSortedPaged(new QueryParameter(params, null), ctx.useXForwardedForAsClientAddr());
    }

    private static void putIfNotNull(Map<String, String> params, String key, String value) {
        if (value != null)
            params.put(key, value);
    }

    private ExchangeSummary summary(AbstractExchange exc) {
        final var req = exc.getRequest();
        final var res = exc.getResponse();
        final var proxy = exc.getProxy();
        final var completed = exc.getStatus() == RECEIVED || exc.getStatus() == COMPLETED;
        final var http2 = exc.getProperty(HTTP2_SERVER) != null;
        return new ExchangeSummary(
                ctx.instanceName(),
                exc.getId(),
                exc.getTime() == null ? null : isoTime(exc.getTime().getTimeInMillis()),
                res == null ? null : res.getStatusCode(),
                proxy == null ? null : ApisResource.apiId(proxy),
                proxy == null ? null : proxy.toString(),
                proxy == null || proxy.getKey().getPort() == -1 ? null : proxy.getKey().getPort(),
                http2 ? "2" : req == null ? null : req.getVersion(),
                req == null ? null : req.getMethod(),
                req == null ? null : req.getUri(),
                getClientAddr(ctx.useXForwardedForAsClientAddr(), exc),
                exc.getServer(),
                proxy instanceof AbstractServiceProxy asp && asp.getTargetPort() != -1 ? asp.getTargetPort() : null,
                req == null ? null : exc.getRequestContentType(),
                req == null || exc.getRequestContentLength() == -1 ? null : (long) exc.getRequestContentLength(),
                res == null ? null : exc.getResponseContentType(),
                res == null || !completed || exc.getResponseContentLength() == -1 ? null : (long) exc.getResponseContentLength(),
                completed,
                ExchangeFilter.duration(exc));
    }

    private static List<NodeStatus> nodeStatus(AbstractExchange exc) {
        final var tracker = exc.getNodeStatusTracker();
        if (tracker == null)
            return null;
        final var codes = tracker.getNodeStatusCodes();
        final var exceptions = tracker.getNodeExceptions();
        final var destinations = exc.getDestinations();
        final var n = Math.max(codes == null ? 0 : codes.length, exceptions == null ? 0 : exceptions.length);
        final var result = new ArrayList<NodeStatus>(n);
        for (int i = 0; i < n; i++) {
            final var exception = exceptions != null && i < exceptions.length ? exceptions[i] : null;
            result.add(new NodeStatus(
                    destinations != null && i < destinations.size() ? destinations.get(i) : null,
                    codes != null && i < codes.length && codes[i] != 0 ? codes[i] : null,
                    exception == null ? null : exception.getMessage()));
        }
        return result;
    }

    private AbstractExchange exchange(ApiRequest req) {
        ctx.requireSelf(req.path(0));
        final AbstractExchange exc;
        try {
            exc = store().getExchangeById(req.pathLong(1));
        } catch (UnsupportedOperationException e) {
            throw notImplemented("The exchange store (%s) cannot look up exchanges.".formatted(storeType(store())));
        }
        if (exc == null)
            throw notFound("Unknown exchange.");
        return exc;
    }

    private Message message(ApiRequest req) {
        return message(exchange(req), req.path(2));
    }

    private static Message message(AbstractExchange exc, String which) {
        final Message msg = isResponse(which) ? exc.getResponse() : exc.getRequest();
        if (msg == null)
            throw notFound("The exchange has no %s (yet).".formatted(which));
        return msg;
    }

    private static boolean isResponse(String which) {
        return "response".equals(which);
    }

    private ExchangeStore store() {
        return ctx.router().getExchangeStore();
    }

    /**
     * Recorded content as text that a browser must never interpret.
     */
    private static Response untrustedText(String text) {
        return Response.ok()
                .contentType(TEXT_PLAIN_UTF8)
                .header(X_CONTENT_TYPE_OPTIONS, "nosniff")
                .header(CONTENT_SECURITY_POLICY, "sandbox")
                .body(text.getBytes(UTF_8))
                .build();
    }

    static boolean isListedInMemory(ExchangeStore store) {
        return store instanceof LimitedMemoryExchangeStore || store instanceof MemoryExchangeStore
               || store instanceof FileExchangeStore || store instanceof ForgetfulExchangeStore;
    }

    /**
     * The file store lists exchanges without their bodies; they are only read from disk for a single exchange.
     */
    private static boolean listsBodies(ExchangeStore store) {
        return !(store instanceof FileExchangeStore);
    }

    static String storeType(ExchangeStore store) {
        return switch (store) {
            case LimitedMemoryExchangeStore ignored -> "limitedMemory";
            case MemoryExchangeStore ignored -> "memory";
            case FileExchangeStore ignored -> "file";
            case ElasticSearchExchangeStore ignored -> "elasticSearch";
            case MongoDBExchangeStore ignored -> "mongoDB";
            case LokiExchangeStore ignored -> "loki";
            case ForgetfulExchangeStore ignored -> "forgetful";
            default -> "other";
        };
    }

    private static <T extends Comparable<T>> List<T> sorted(Collection<T> values) {
        return values.stream().filter(Objects::nonNull).sorted().toList();
    }
}
