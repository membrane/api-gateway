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

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.exchangestore.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.proxies.*;

import java.lang.management.*;
import java.util.*;

import static com.predic8.membrane.core.interceptor.administration.v1.ApiJson.*;
import static java.util.Comparator.*;

/**
 * {@code /instances}, {@code /auth/me}, {@code /statistics}, {@code /streams} and {@code /system}.
 */
final class MonitoringResource {

    static final String API_VERSION = "1.0.0";

    record Instance(String name, boolean self, String status, String version, String apiVersion, String startedAt,
                    boolean readOnly) {
    }

    record Instances(String self, List<Instance> instances) {
    }

    record ApiStatistics(String instance, String apiId, String name, ApisResource.Statistics totals,
                         Map<String, ApisResource.Statistics> byStatusCode, Integer openBackendConnections) {
    }

    record ApiStatisticsList(List<ApiStatistics> apis) {
    }

    record ClientStatistic(String instance, String client, long count, long min, long max, long avg) {
    }

    record ClientPage(long total, int offset, int limit, List<ClientStatistic> clients) {
    }

    record StreamPumpInfo(String instance, String name, String proxy, String createdAt, long activeSeconds,
                          long transferredBytes) {
    }

    record StreamPumps(List<StreamPumpInfo> streams) {
    }

    record Heap(long used, long committed, long max) {
    }

    record Threads(int live, int peak, int daemon) {
    }

    record Gc(String name, long count, long timeMillis) {
    }

    record SystemInfo(String instance, String membraneVersion, String javaVersion, String os, String startedAt,
                      long uptimeSeconds, Heap heap, Threads threads, List<Gc> gc, int cpus) {
    }

    record SystemInfos(List<SystemInfo> instances) {
    }

    private static final Map<String, Comparator<ClientStatistics>> CLIENT_SORTS = Map.of(
            "client", comparing(ClientStatistics::getClient, nullsFirst(naturalOrder())),
            "count", comparingInt(ClientStatistics::getCount),
            "min", comparingLong(ClientStatistics::getMinDuration),
            "max", comparingLong(ClientStatistics::getMaxDuration),
            "avg", comparingLong(ClientStatistics::getAvgDuration));

    private final ConsoleContext ctx;

    MonitoringResource(ConsoleContext ctx) {
        this.ctx = ctx;
    }

    Response instances(ApiRequest req) {
        return ok(new Instances(ctx.instanceName(), List.of(new Instance(ctx.instanceName(), true, "up",
                Constants.VERSION, API_VERSION, isoTime(ctx.startedAt()), ctx.readOnly()))));
    }

    Response me(ApiRequest req, Principal principal) {
        return ok(principal);
    }

    Response apiStatistics(ApiRequest req) {
        final var result = new ArrayList<ApiStatistics>();
        for (final var proxy : ctx.router().getRuleManager().getRules()) {
            final var totals = new StatisticCollector(true);
            final var byStatus = new TreeMap<String, ApisResource.Statistics>();
            for (final var e : proxy.getStatisticCollector().getStatisticsByStatusCodes().entrySet()) {
                final var copy = new StatisticCollector(true);
                synchronized (e.getValue()) {
                    copy.collectFrom(e.getValue());
                }
                totals.collectFrom(copy);
                byStatus.put(String.valueOf(e.getKey()), ApisResource.statistics(copy));
            }
            result.add(new ApiStatistics(ctx.instanceName(), ApisResource.apiId(proxy), proxy.getName(),
                    ApisResource.statistics(totals), byStatus,
                    openBackendConnections(proxy.getKey().getPort())));
        }
        return ok(new ApiStatisticsList(result));
    }

    /**
     * @return null if the transport does not count connections (it then answers "N/A")
     */
    private Integer openBackendConnections(int port) {
        try {
            return Integer.parseInt(ctx.router().getTransport().getOpenBackendConnections(port).trim());
        } catch (RuntimeException e) {
            return null;
        }
    }

    Response clientStatistics(ApiRequest req) {
        final var sort = req.enumParam("sort", "client", CLIENT_SORTS.keySet());
        final var desc = "desc".equals(req.enumParam("order", "asc", Set.of("asc", "desc")));
        final var offset = req.intParam("offset", 0, 0, Integer.MAX_VALUE);
        final var limit = req.intParam("limit", 50, 1, 1000);
        final var clients = new ArrayList<ClientStatistics>(ctx.router().getExchangeStore().getClientStatistics());
        clients.sort(desc ? CLIENT_SORTS.get(sort).reversed() : CLIENT_SORTS.get(sort));
        final var page = clients.stream().skip(offset).limit(limit)
                .map(c -> new ClientStatistic(ctx.instanceName(), c.getClient(), c.getCount(),
                        c.getMinDuration(), c.getMaxDuration(), c.getAvgDuration()))
                .toList();
        return ok(new ClientPage(clients.size(), offset, limit, page));
    }

    Response streams(ApiRequest req) {
        final var now = System.currentTimeMillis();
        final var pumps = ctx.router().getStatistics().getStreamPumpStats().getStreamPumps().stream()
                .map(p -> new StreamPumpInfo(ctx.instanceName(), p.getName(), p.getServiceProxyName(),
                        isoTime(p.getCreationTime()), (now - p.getCreationTime()) / 1000, p.getTransferredBytes()))
                .toList();
        return ok(new StreamPumps(pumps));
    }

    Response system(ApiRequest req) {
        final var memory = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        final var threads = ManagementFactory.getThreadMXBean();
        final var runtime = ManagementFactory.getRuntimeMXBean();
        final var gc = ManagementFactory.getGarbageCollectorMXBeans().stream()
                .map(b -> new Gc(b.getName(), b.getCollectionCount(), b.getCollectionTime()))
                .toList();
        return ok(new SystemInfos(List.of(new SystemInfo(ctx.instanceName(), Constants.VERSION,
                System.getProperty("java.version"),
                "%s %s (%s)".formatted(System.getProperty("os.name"), System.getProperty("os.version"), System.getProperty("os.arch")),
                isoTime(runtime.getStartTime()), runtime.getUptime() / 1000,
                new Heap(memory.getUsed(), memory.getCommitted(), memory.getMax()),
                new Threads(threads.getThreadCount(), threads.getPeakThreadCount(), threads.getDaemonThreadCount()),
                gc, Runtime.getRuntime().availableProcessors()))));
    }
}
