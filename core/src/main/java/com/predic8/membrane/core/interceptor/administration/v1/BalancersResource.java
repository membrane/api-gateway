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
import com.predic8.membrane.core.interceptor.balancer.*;

import java.security.*;
import java.util.*;

import static com.predic8.membrane.core.interceptor.administration.v1.ApiException.*;
import static com.predic8.membrane.core.interceptor.administration.v1.ApiJson.*;
import static java.nio.charset.StandardCharsets.*;

/**
 * {@code /balancers}: load balancers, their clusters and nodes.
 */
final class BalancersResource {

    record NodeInfo(String host, int port, String status, long count, double errorRate, String lastUp, int sessions,
                    int threads) {
    }

    record ClusterInfo(String name, List<NodeInfo> nodes) {
    }

    record BalancerInfo(String instance, String name, boolean failover, List<ClusterInfo> clusters) {
    }

    record Balancers(List<BalancerInfo> balancers) {
    }

    record SessionInfo(String id, String lastUsed) {
    }

    record NodeDetail(String host, int port, String status, long count, double errorRate, String lastUp,
                      int sessions, int threads, Map<String, Long> byStatusCode, List<SessionInfo> sessionList) {
    }

    private final ConsoleContext ctx;

    BalancersResource(ConsoleContext ctx) {
        this.ctx = ctx;
    }

    Response list(ApiRequest req) {
        final var result = new ArrayList<BalancerInfo>();
        for (final var lbi : BalancerUtil.collectBalancers(ctx.router())) {
            final var balancer = lbi.getClusterManager();
            final var clusters = balancer.getClusters().stream()
                    .map(c -> new ClusterInfo(c.getName(), balancer.getAllNodesByCluster(c.getName()).stream()
                            .map(n -> nodeInfo(balancer, c.getName(), n))
                            .toList()))
                    .toList();
            result.add(new BalancerInfo(ctx.instanceName(), lbi.getName(), lbi.isFailOver(), clusters));
        }
        return ok(new Balancers(result));
    }

    Response createCluster(ApiRequest req) {
        final var balancer = balancer(req);
        final var name = requiredString(ApiJson.readObject(req.exchange().getRequest()), "name");
        if (balancer.getClusters().stream().anyMatch(c -> c.getName().equals(name)))
            throw new ApiException(409, "Conflict.", "The cluster exists already.");
        balancer.addCluster(name);
        return created(new ClusterInfo(name, List.of()));
    }

    Response createNode(ApiRequest req) {
        final var balancer = balancer(req);
        final var cluster = cluster(balancer, req.path(2));
        final var body = ApiJson.readObject(req.exchange().getRequest());
        final var host = requiredString(body, "host");
        final var port = port(body.get("port"));
        if (balancer.hasNode(cluster, host, port))
            throw new ApiException(409, "Conflict.", "The node exists already.");
        balancer.up(cluster, host, port);
        return created(nodeInfo(balancer, cluster, balancer.getNode(cluster, host, port)));
    }

    Response getNode(ApiRequest req) {
        final var balancer = balancer(req);
        final var cluster = cluster(balancer, req.path(2));
        final var node = node(balancer, cluster, req.path(3));
        final var info = nodeInfo(balancer, cluster, node);
        final var byStatus = new TreeMap<String, Long>();
        for (final var e : node.getStatisticsByStatusCodes().entrySet())
            byStatus.put(String.valueOf(e.getKey()), (long) e.getValue().getCount());
        final var sessions = balancer.getSessionsByNode(cluster, node).stream()
                .map(s -> new SessionInfo(fingerprint(s.getId()), isoTime(s.getLastUsed())))
                .toList();
        return ok(new NodeDetail(info.host(), info.port(), info.status(), info.count(), info.errorRate(),
                info.lastUp(), info.sessions(), info.threads(), byStatus, sessions));
    }

    Response deleteNode(ApiRequest req) {
        final var balancer = balancer(req);
        final var cluster = cluster(balancer, req.path(2));
        final var node = node(balancer, cluster, req.path(3));
        balancer.removeNode(cluster, node.getHost(), node.getPort());
        return noContent();
    }

    /**
     * Only existing nodes can be changed, so this cannot be used to add a backend.
     */
    Response changeNode(ApiRequest req) {
        final var balancer = balancer(req);
        final var cluster = cluster(balancer, req.path(2));
        final var node = node(balancer, cluster, req.path(3));
        switch (req.path(4)) {
            case "up" -> balancer.up(cluster, node.getHost(), node.getPort());
            case "down" -> balancer.down(cluster, node.getHost(), node.getPort());
            case "takeout" -> balancer.takeout(cluster, node.getHost(), node.getPort());
            case "reset" -> node.clearCounter();
            default -> throw notFound("Unknown node action.");
        }
        return ok(nodeInfo(balancer, cluster, balancer.getNode(cluster, node.getHost(), node.getPort())));
    }

    private Balancer balancer(ApiRequest req) {
        ctx.requireSelf(req.path(0));
        return BalancerUtil.collectBalancers(ctx.router()).stream()
                .filter(lbi -> lbi.getName() != null && lbi.getName().equalsIgnoreCase(req.path(1)))
                .map(LoadBalancingInterceptor::getClusterManager)
                .findFirst()
                .orElseThrow(() -> notFound("Unknown balancer."));
    }

    private static String cluster(Balancer balancer, String name) {
        if (balancer.getClusters().stream().noneMatch(c -> c.getName().equals(name)))
            throw notFound("Unknown cluster.");
        return name;
    }

    private static Node node(Balancer balancer, String cluster, String hostPort) {
        final var colon = hostPort.lastIndexOf(':');
        if (colon < 1)
            throw notFound("Unknown node.");
        final var host = hostPort.substring(0, colon);
        final int port;
        try {
            port = Integer.parseInt(hostPort.substring(colon + 1));
        } catch (NumberFormatException e) {
            throw notFound("Unknown node.");
        }
        if (!balancer.hasNode(cluster, host, port))
            throw notFound("Unknown node.");
        return balancer.getNode(cluster, host, port);
    }

    private static NodeInfo nodeInfo(Balancer balancer, String cluster, Node n) {
        return new NodeInfo(n.getHost(), n.getPort(), String.valueOf(n.getStatus()), n.getCounter(), n.getErrors(),
                isoTime(n.getLastUpTime()), balancer.getSessionsByNode(cluster, n).size(), n.getThreads());
    }

    private static String requiredString(Map<String, Object> body, String name) {
        if (!(body.get(name) instanceof String s) || s.isBlank())
            throw badRequest("Property '%s' is required.".formatted(name));
        return s;
    }

    private static int port(Object value) {
        if (!(value instanceof Integer port) || port < 1 || port > 65535)
            throw badRequest("Property 'port' must be an integer between 1 and 65535.");
        return port;
    }

    /**
     * Session ids are credentials of the balanced clients; the API only shows a short fingerprint.
     */
    static String fingerprint(String sessionId) {
        if (sessionId == null)
            return null;
        try {
            final var hash = MessageDigest.getInstance("SHA-256").digest(sessionId.getBytes(UTF_8));
            return HexFormat.of().formatHex(hash, 0, 6);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
