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

import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.administration.*;
import com.predic8.membrane.core.interceptor.balancer.*;
import com.predic8.membrane.core.openapi.serviceproxy.*;
import com.predic8.membrane.core.proxies.*;

import java.util.*;
import java.util.function.*;

import static com.predic8.membrane.core.interceptor.administration.v1.ApiException.*;
import static com.predic8.membrane.core.interceptor.administration.v1.ApiJson.*;
import static java.util.Comparator.*;

/**
 * {@code /apis} and {@code /transport/flow}.
 */
final class ApisResource {

    static final Set<String> KNOWN_TYPES = Set.of("api", "serviceProxy", "soapProxy", "proxy", "internal", "stompProxy", "kafkaConsumer");

    static final Map<String, Comparator<Indexed>> SORTS = Map.of(
            "order", comparingInt(Indexed::order),
            "name", comparing(i -> String.valueOf(i.proxy().getName()), String.CASE_INSENSITIVE_ORDER),
            "type", comparing(i -> type(i.proxy())),
            "port", comparingInt(i -> i.proxy().getKey().getPort()),
            "count", comparingInt(i -> i.proxy().getStatisticCollector().getCount()));

    record Match(Integer port, String ip, String host, String method, String path, String pathType, String test,
                 Boolean tls) {
    }

    record Target(String url, String host, Integer port, Boolean tls, String balancer) {
    }

    record Statistics(long count, long errors, Integer min, Integer max, Double avg, long requestBytes,
                      long responseBytes) {
    }

    record ApiSummary(String instance, String apiId, String name, String type, int order, boolean active,
                      String error, Match match, Target target, boolean hasOpenApi, long count) {
    }

    record ApiDetail(String instance, String apiId, String name, String type, int order, boolean active,
                     String error, Match match, Target target, boolean hasOpenApi, long count,
                     Statistics totals, Map<String, Statistics> byStatusCode) {
    }

    record ApiPage(long total, int offset, int limit, List<ApiSummary> apis) {
    }

    record FlowResponse(List<FlowTree.FlowNode> flow) {
    }

    record InstanceFlow(String instance, List<FlowTree.FlowNode> flow) {
    }

    record TransportFlowResponse(List<InstanceFlow> instances) {
    }

    private record Indexed(Proxy proxy, int order) {
    }

    private final ConsoleContext ctx;

    ApisResource(ConsoleContext ctx) {
        this.ctx = ctx;
    }

    Response list(ApiRequest req) {
        final var types = req.params("type");
        for (final var t : types)
            if (!KNOWN_TYPES.contains(t) && !"other".equals(t))
                throw badRequest("Unknown API type '%s'.".formatted(t));
        final var q = req.param("q").map(s -> s.toLowerCase(Locale.ROOT));
        final var sort = req.enumParam("sort", "order", SORTS.keySet());
        final var desc = "desc".equals(req.enumParam("order", "asc", Set.of("asc", "desc")));
        final var offset = req.intParam("offset", 0, 0, Integer.MAX_VALUE);
        final var limit = req.intParam("limit", 50, 1, 1000);
        if (ctx.isExcluded(req))
            return ok(new ApiPage(0, offset, limit, List.of()));

        final var all = indexed();
        final var filtered = all.stream()
                .filter(i -> types.isEmpty() || types.contains(type(i.proxy())))
                .filter(i -> q.isEmpty() || matchesText(i.proxy(), q.get()))
                .sorted(desc ? SORTS.get(sort).reversed() : SORTS.get(sort))
                .toList();

        final var page = filtered.stream().skip(offset).limit(limit)
                .map(i -> summary(i.proxy(), i.order()))
                .toList();
        return ok(new ApiPage(filtered.size(), offset, limit, page));
    }

    Response get(ApiRequest req) {
        ctx.requireSelf(req.path(0));
        final var i = find(req.path(1));
        final var s = summary(i.proxy(), i.order());
        final var byStatus = new TreeMap<String, Statistics>();
        final var totals = new StatisticCollector(true);
        for (final var e : i.proxy().getStatisticCollector().getStatisticsByStatusCodes().entrySet()) {
            final var copy = new StatisticCollector(true);
            synchronized (e.getValue()) {
                copy.collectFrom(e.getValue());
            }
            totals.collectFrom(copy);
            byStatus.put(String.valueOf(e.getKey()), statistics(copy));
        }
        return ok(new ApiDetail(s.instance(), s.apiId(), s.name(), s.type(), s.order(), s.active(), s.error(),
                s.match(), s.target(), s.hasOpenApi(), s.count(), statistics(totals), byStatus));
    }

    Response delete(ApiRequest req) {
        ctx.requireSelf(req.path(0));
        ctx.router().getRuleManager().removeRule(find(req.path(1)).proxy());
        return noContent();
    }

    Response start(ApiRequest req) throws CloneNotSupportedException {
        ctx.requireSelf(req.path(0));
        final var i = find(req.path(1));
        final var restarted = i.proxy().clone();
        ctx.router().getRuleManager().replaceRule(i.proxy(), restarted);
        return ok(summary(restarted, i.order()));
    }

    Response flow(ApiRequest req) {
        ctx.requireSelf(req.path(0));
        return ok(new FlowResponse(FlowTree.of(find(req.path(1)).proxy().getFlow())));
    }

    Response transportFlow(ApiRequest req) {
        return ok(new TransportFlowResponse(List.of(
                new InstanceFlow(ctx.instanceName(), FlowTree.of(ctx.router().getTransport().getFlow())))));
    }

    static Statistics statistics(StatisticCollector sc) {
        return new Statistics(sc.getCount(), sc.getErrors(),
                sc.getMinTimeMillis() < 0 ? null : sc.getMinTimeMillis(),
                sc.getMaxTimeMillis() < 0 ? null : sc.getMaxTimeMillis(),
                sc.getAvgTimeMillis() < 0 ? null : sc.getAvgTimeMillis(),
                sc.getGoodTotalBytesSent(), sc.getGoodTotalBytesReceived());
    }

    static String apiId(Proxy proxy) {
        return RuleUtil.getRuleIdentifier(proxy);
    }

    static String type(Proxy proxy) {
        final var name = FlowTree.elementName(proxy);
        return KNOWN_TYPES.contains(name) ? name : "other";
    }

    private List<Indexed> indexed() {
        final var rules = ctx.router().getRuleManager().getRules();
        final var result = new ArrayList<Indexed>(rules.size());
        for (int i = 0; i < rules.size(); i++)
            result.add(new Indexed(rules.get(i), i + 1));
        return result;
    }

    private Indexed find(String apiId) {
        return indexed().stream()
                .filter(i -> apiId(i.proxy()).equals(apiId))
                .findFirst()
                .orElseThrow(() -> notFound("Unknown API."));
    }

    private ApiSummary summary(Proxy proxy, int order) {
        return new ApiSummary(ctx.instanceName(), apiId(proxy), proxy.getName(), type(proxy), order,
                proxy.isActive(), proxy.isActive() ? null : proxy.getErrorState(),
                match(proxy), target(proxy), hasOpenApi(proxy), proxy.getStatisticCollector().getCount());
    }

    private static boolean matchesText(Proxy proxy, String q) {
        final var key = proxy.getKey();
        final Function<String, Boolean> contains = s -> s != null && s.toLowerCase(Locale.ROOT).contains(q);
        return contains.apply(proxy.getName()) || contains.apply(key.getPath()) || contains.apply(key.getHost())
               || (proxy instanceof AbstractServiceProxy asp
                   && (contains.apply(asp.getTargetURL()) || contains.apply(asp.getTargetHost())));
    }

    private static Match match(Proxy proxy) {
        final var key = proxy.getKey();
        final var path = key.getPath();
        return new Match(
                key.getPort() == -1 ? null : key.getPort(),
                key.getIp(),
                key.getHost(),
                key.getMethod(),
                path,
                path == null ? null : key.isPathRegExp() ? "regex" : key.isUsePathPattern() ? "uriTemplate" : "prefix",
                proxy instanceof APIProxy api ? api.getTest() : null,
                proxy instanceof SSLableProxy sp ? sp.isInboundSSL() : null);
    }

    private static Target target(Proxy proxy) {
        final var balancer = proxy.getFlow() == null ? null : proxy.getFlow().stream()
                .filter(LoadBalancingInterceptor.class::isInstance)
                .map(i -> ((LoadBalancingInterceptor) i).getName())
                .findFirst().orElse(null);
        if (!(proxy instanceof AbstractServiceProxy asp) || asp.getTarget() == null)
            return balancer == null ? null : new Target(null, null, null, null, balancer);
        final var t = asp.getTarget();
        if (t.getUrl() == null && t.getHost() == null && balancer == null)
            return null;
        return new Target(t.getUrl(), t.getHost(), t.getHost() == null ? null : t.getPort(),
                t.getSslParser() != null || (t.getUrl() != null && t.getUrl().startsWith("https:")), balancer);
    }

    private static boolean hasOpenApi(Proxy proxy) {
        return proxy instanceof APIProxy api && api.getOpenapi() != null && !api.getOpenapi().isEmpty();
    }
}
