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

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.administration.*;
import com.predic8.membrane.core.interceptor.administration.v1.AdminApiV1Interceptor.*;
import com.predic8.membrane.core.interceptor.administration.v1.PeerClient.*;
import org.slf4j.*;

import java.net.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

import static com.predic8.membrane.core.interceptor.administration.v1.ApiException.*;
import static com.predic8.membrane.core.interceptor.administration.v1.ApiJson.*;
import static java.nio.charset.StandardCharsets.*;

/**
 * Answers admin API requests of a console with {@code peers}: requests for one instance are forwarded to it, lists
 * are collected from all instances in parallel and merged. Thread safe.
 */
final class Aggregator {

    private static final Logger log = LoggerFactory.getLogger(Aggregator.class);

    /**
     * Paging across instances fetches the first {@code offset + limit} entries of every instance.
     */
    static final int MAX_MERGED_WINDOW = 1000;

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration FORWARD_TIMEOUT = Duration.ofSeconds(60);

    /**
     * Result of one instance for a list request.
     *
     * @param body null unless {@code status} is {@code ok}
     */
    private record Part(String instance, String status, String error, JsonNode body) {
        boolean ok() {
            return "ok".equals(status);
        }
    }

    private final ConsoleContext ctx;
    private final List<AdminConsolePeer> peers;
    private final PeerClient client;
    private final Map<AdminConsolePeer, String> learnedNames = new ConcurrentHashMap<>();
    private final ExecutorService localCalls = Executors.newVirtualThreadPerTaskExecutor();

    Aggregator(ConsoleContext ctx, List<AdminConsolePeer> peers, PeerClient client) {
        this.ctx = ctx;
        this.peers = List.copyOf(peers);
        this.client = client;
    }

    Response handle(Route route, ApiRequest req, Principal principal, String rawPath, String rawQuery) throws Exception {
        return switch (route.scope()) {
            case LOCAL -> route.handler().handle(req, principal);
            case INSTANCE -> forOneInstance(route, req, principal, rawPath, rawQuery);
            case COLLECTION -> collect(route, req, principal, rawPath);
        };
    }

    // ------------------------------------------------------------------ one instance

    private Response forOneInstance(Route route, ApiRequest req, Principal principal, String rawPath, String rawQuery) throws Exception {
        final var instance = req.path(0);
        if (ctx.instanceName().equals(instance))
            return route.handler().handle(req, principal);
        final var peer = peerNamed(instance).orElseThrow(() -> notFound("Unknown instance."));
        final var msg = req.exchange().getRequest();
        final var body = msg.isBodyEmpty() ? null : msg.getBodyAsStreamDecoded().readAllBytes();
        final PeerResponse res;
        try {
            res = client.send(peer, msg.getMethod(), rawPath + (rawQuery == null ? "" : "?" + rawQuery),
                    msg.getHeader().getContentType(), body, FORWARD_TIMEOUT).get();
        } catch (ExecutionException e) {
            log.info("Admin API peer {} is not reachable: {}", peer.getUrl(), e.getCause().getMessage());
            throw new ApiException(502, "Bad gateway.", "The instance '%s' is not reachable.".formatted(instance));
        }
        final var builder = Response.statusCode(res.status());
        res.headers().forEach(builder::header);
        if (res.body().length > 0)
            builder.body(res.body());
        return builder.build();
    }

    private Optional<AdminConsolePeer> peerNamed(String instance) {
        final var known = peers.stream().filter(p -> instance.equals(name(p))).findFirst();
        if (known.isPresent())
            return known;
        learnNames();
        return peers.stream().filter(p -> instance.equals(name(p))).findFirst();
    }

    /**
     * @return the configured or learned name, or null if the peer did not tell it yet
     */
    private String name(AdminConsolePeer peer) {
        return peer.getName() != null ? peer.getName() : learnedNames.get(peer);
    }

    private String label(AdminConsolePeer peer) {
        final var name = name(peer);
        return name != null ? name : peer.getUrl();
    }

    /**
     * Asks peers without a known name for it.
     */
    private void learnNames() {
        final var unnamed = peers.stream().filter(p -> name(p) == null).toList();
        final var futures = unnamed.stream()
                .map(p -> client.send(p, "GET", AdminApiV1Interceptor.PREFIX + "/instances", null, null, REQUEST_TIMEOUT)
                        .thenAccept(r -> remember(p, r)))
                .toList();
        for (final var f : futures) {
            try {
                f.get();
            } catch (Exception e) {
                // peer down: stays unnamed
            }
        }
    }

    private void remember(AdminConsolePeer peer, PeerResponse response) {
        if (response.status() != 200)
            return;
        try {
            final var self = MAPPER.readTree(response.body()).path("self");
            if (self.isTextual())
                learnedNames.put(peer, self.asText());
        } catch (Exception e) {
            // not an admin API
        }
    }

    // ------------------------------------------------------------------ lists

    private Response collect(Route route, ApiRequest req, Principal principal, String rawPath) throws Exception {
        final var merge = route.merge();
        if (peers.stream().anyMatch(p -> name(p) == null))
            learnNames();
        return switch (merge.kind()) {
            case INSTANCES -> ok(instances(route, req, principal));
            case CONCAT -> ok(concat(merge.field(), fetch(route, req, principal, rawPath, Map.of())));
            case FACETS -> ok(facets(fetch(route, req, principal, rawPath, Map.of())));
            case PAGE -> ok(page(route, req, principal, rawPath));
        };
    }

    private ObjectNode page(Route route, ApiRequest req, Principal principal, String rawPath) throws Exception {
        final var merge = route.merge();
        final var offset = req.intParam("offset", 0, 0, Integer.MAX_VALUE);
        final var limit = req.intParam("limit", 50, 1, MAX_MERGED_WINDOW);
        if (offset + limit > MAX_MERGED_WINDOW)
            throw badRequest("Across several instances, offset + limit may not exceed %d.".formatted(MAX_MERGED_WINDOW));
        final var sort = req.param("sort").orElse(merge.defaultSort());
        final var desc = "desc".equals(req.param("order").orElse(merge.defaultOrder()));

        if (req.param("waitForModification").isPresent())
            waitForAnyModification(route, req, principal, rawPath);

        final var window = new HashMap<String, String>();
        window.put("offset", "0");
        window.put("limit", String.valueOf(offset + limit));
        window.put("waitForModification", null);
        window.put("timeout", null);
        final var parts = fetch(route, req, principal, rawPath, window);

        final var items = new ArrayList<JsonNode>();
        long total = 0;
        final var lastModified = MAPPER.createObjectNode();
        for (final var part : parts) {
            if (!part.ok())
                continue;
            part.body().path(merge.field()).forEach(items::add);
            total += part.body().path("total").asLong();
            if (part.body().has("lastModified"))
                lastModified.setAll((ObjectNode) part.body().get("lastModified"));
        }
        items.sort(comparator(sort, desc));

        final var result = MAPPER.createObjectNode();
        result.put("total", total);
        result.put("offset", offset);
        result.put("limit", limit);
        result.set("instances", status(parts));
        final var page = result.putArray(merge.field());
        items.stream().skip(offset).limit(limit).forEach(page::add);
        if ("exchanges".equals(merge.field()))
            result.set("lastModified", lastModified);
        return result;
    }

    /**
     * Long poll on all instances; returns when any of them reports a modification or all timed out.
     */
    private void waitForAnyModification(Route route, ApiRequest req, Principal principal, String rawPath) throws Exception {
        final var timeout = req.intParam("timeout", 30, 1, 60);
        final var futures = new ArrayList<CompletableFuture<?>>();
        futures.add(CompletableFuture.runAsync(() -> {
            try {
                route.handler().handle(req, principal);
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, localCalls));
        for (final var peer : selectedPeers(req))
            futures.add(client.send(peer, "GET", rawPath + "?" + query(req, Map.of()), null, null,
                    Duration.ofSeconds(timeout + 10L)));
        try {
            CompletableFuture.anyOf(futures.toArray(CompletableFuture[]::new)).get(timeout + 10L, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof ApiException ae)
                throw ae;
            // a peer is down: answer with what the others have
        } catch (TimeoutException e) {
            // nothing changed anywhere
        }
    }

    private ObjectNode instances(Route route, ApiRequest req, Principal principal) throws Exception {
        final var self = local(route, req, principal, Map.of());
        final var result = MAPPER.createObjectNode();
        result.put("self", ctx.instanceName());
        final var list = result.putArray("instances");
        self.body().path("instances").forEach(list::add);

        final var futures = new LinkedHashMap<AdminConsolePeer, CompletableFuture<PeerResponse>>();
        for (final var peer : peers)
            futures.put(peer, client.send(peer, "GET", AdminApiV1Interceptor.PREFIX + "/instances", null, null, REQUEST_TIMEOUT));
        for (final var e : futures.entrySet()) {
            final var peer = e.getKey();
            final var part = toPart(peer, e.getValue());
            if (part.ok()) {
                if (part.body().path("self").isTextual())
                    learnedNames.putIfAbsent(peer, part.body().get("self").asText());
                for (final var instance : part.body().path("instances")) {
                    if (!instance.path("self").asBoolean())
                        continue;
                    final var copy = ((ObjectNode) instance).deepCopy();
                    copy.put("self", false);
                    copy.put("url", peer.getUrl());
                    list.add(copy);
                }
            } else {
                final var down = list.addObject();
                down.put("name", label(peer));
                down.put("url", peer.getUrl());
                down.put("self", false);
                down.put("status", "unauthorized".equals(part.status()) ? "unauthorized" : "down");
                if (part.error() != null)
                    down.put("error", part.error());
            }
        }
        return result;
    }

    private static ObjectNode concat(String field, List<Part> parts) {
        final var result = MAPPER.createObjectNode();
        final var array = result.putArray(field);
        for (final var part : parts)
            if (part.ok())
                part.body().path(field).forEach(array::add);
        return result;
    }

    private static ObjectNode facets(List<Part> parts) {
        final var values = new TreeMap<String, TreeSet<JsonNode>>();
        final Comparator<JsonNode> byValue = (a, b) -> a.isNumber() && b.isNumber()
                ? Double.compare(a.asDouble(), b.asDouble())
                : a.asText().compareTo(b.asText());
        for (final var part : parts) {
            if (!part.ok())
                continue;
            part.body().fields().forEachRemaining(e -> {
                final var set = values.computeIfAbsent(e.getKey(), k -> new TreeSet<>(byValue));
                e.getValue().forEach(set::add);
            });
        }
        final var result = MAPPER.createObjectNode();
        values.forEach((k, v) -> v.forEach(result.withArray(k)::add));
        return result;
    }

    private static ArrayNode status(List<Part> parts) {
        final var array = MAPPER.createArrayNode();
        for (final var part : parts) {
            final var o = array.addObject();
            o.put("instance", part.instance());
            o.put("status", part.status());
            if (part.error() != null)
                o.put("error", part.error());
        }
        return array;
    }

    /**
     * Calls this instance and the selected peers in parallel.
     *
     * @param overrides query parameters to replace; a null value removes the parameter
     */
    private List<Part> fetch(Route route, ApiRequest req, Principal principal, String rawPath, Map<String, String> overrides) throws Exception {
        final var selected = req.params("instance");
        final var futures = new LinkedHashMap<AdminConsolePeer, CompletableFuture<PeerResponse>>();
        for (final var peer : selectedPeers(req))
            futures.put(peer, client.send(peer, "GET", rawPath + "?" + query(req, overrides), null, null, REQUEST_TIMEOUT));

        final var parts = new ArrayList<Part>();
        if (selected.isEmpty() || selected.contains(ctx.instanceName()))
            parts.add(local(route, req, principal, overrides));
        futures.forEach((peer, future) -> parts.add(toPart(peer, future)));
        return parts;
    }

    private List<AdminConsolePeer> selectedPeers(ApiRequest req) {
        final var selected = req.params("instance");
        return peers.stream().filter(p -> selected.isEmpty() || selected.contains(label(p))).toList();
    }

    private Part local(Route route, ApiRequest req, Principal principal, Map<String, String> overrides) throws Exception {
        final var query = new HashMap<>(req.query());
        overrides.forEach((k, v) -> {
            if (v == null)
                query.remove(k);
            else
                query.put(k, List.of(v));
        });
        try {
            final var res = route.handler().handle(new ApiRequest(req.exchange(), req.pathParams(), query), principal);
            return new Part(ctx.instanceName(), "ok", null, MAPPER.readTree(res.getBodyAsStreamDecoded()));
        } catch (ApiException e) {
            if (e.getStatus() == 501)
                return new Part(ctx.instanceName(), "unsupported", e.getMessage(), null);
            throw e;
        }
    }

    private Part toPart(AdminConsolePeer peer, CompletableFuture<PeerResponse> future) {
        try {
            final var res = future.get();
            if (res.status() / 100 == 2)
                return new Part(label(peer), "ok", null, MAPPER.readTree(res.body()));
            final var detail = problemDetail(res.body());
            return switch (res.status()) {
                case 401, 403 -> new Part(label(peer), "unauthorized", detail, null);
                case 501 -> new Part(label(peer), "unsupported", detail, null);
                default -> new Part(label(peer), "error", detail != null ? detail : "Status " + res.status(), null);
            };
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Part(label(peer), "down", "Interrupted.", null);
        } catch (Exception e) {
            final var cause = e instanceof ExecutionException && e.getCause() != null ? e.getCause() : e;
            return new Part(label(peer), "down", cause.getClass().getSimpleName(), null);
        }
    }

    private static String problemDetail(byte[] body) {
        try {
            final var detail = MAPPER.readTree(body).path("detail");
            return detail.isTextual() ? detail.asText() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * The query string of the request, with {@code overrides} applied.
     */
    private static String query(ApiRequest req, Map<String, String> overrides) {
        final var joiner = new StringJoiner("&");
        req.query().forEach((k, values) -> {
            if (overrides.containsKey(k))
                return;
            for (final var v : values)
                joiner.add(encode(k) + "=" + encode(v == null ? "" : v));
        });
        overrides.forEach((k, v) -> {
            if (v != null)
                joiner.add(encode(k) + "=" + encode(v));
        });
        return joiner.toString();
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, UTF_8);
    }

    // ------------------------------------------------------------------ sorting

    static Comparator<JsonNode> comparator(String sort, boolean desc) {
        final Function<JsonNode, JsonNode> key = "port".equals(sort)
                ? n -> n.path("match").path("port")
                : n -> n.path(sort);
        Comparator<JsonNode> byKey = (a, b) -> compareValues(sort, key.apply(a), key.apply(b));
        if (desc)
            byKey = byKey.reversed();
        return byKey
                .thenComparing(n -> n.path("instance").asText())
                .thenComparingLong(n -> n.path("id").asLong());
    }

    private static int compareValues(String sort, JsonNode a, JsonNode b) {
        final var aMissing = a.isMissingNode() || a.isNull();
        final var bMissing = b.isMissingNode() || b.isNull();
        if (aMissing || bMissing)
            return Boolean.compare(aMissing, bMissing);
        if ("time".equals(sort))
            return Instant.parse(a.asText()).compareTo(Instant.parse(b.asText()));
        if (a.isNumber() && b.isNumber())
            return Double.compare(a.asDouble(), b.asDouble());
        return a.asText().compareToIgnoreCase(b.asText());
    }
}
