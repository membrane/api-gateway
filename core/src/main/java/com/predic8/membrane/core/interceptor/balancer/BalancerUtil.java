/* Copyright 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor.balancer;

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.chain.ChainDef;
import com.predic8.membrane.core.proxies.*;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

public class BalancerUtil {

    private static Stream<List<Interceptor>> allFlows(Router router) {
        return Stream.of(
                router.getRuleManager()
                        .getRules()
                        .stream()
                        .map(Proxy::getFlow),
                Optional.ofNullable(router.getBeanFactory())
                        .map(ctx -> ctx.getBeansOfType(ChainDef.class)
                                .values()
                                .stream()
                                .map(ChainDef::getFlow))
                        .orElseGet(Stream::empty),
                Stream.of(router.getGlobalInterceptor().getFlow())
        ).flatMap(Function.identity());
    }

    public static List<LoadBalancingInterceptor> collectBalancers(Router router) {
        return allFlows(router)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .filter(LoadBalancingInterceptor.class::isInstance)
                .map(LoadBalancingInterceptor.class::cast)
                .toList();
    }

    public static List<Cluster> collectClusters(Router router) {
        return Stream.concat(
                collectBalancers(router).stream()
                        .flatMap(lbi -> lbi.getClusterManager()
                                .getClusters()
                                .stream()),
                Optional.ofNullable(router.getBeanFactory())
                        .map(ctx -> ctx.getBeansOfType(Balancer.class)
                                .values()
                                .stream()
                                .flatMap(b -> b.getClusters().stream()))
                        .orElseGet(Stream::empty)
        ).toList();
    }

    public static Balancer lookupBalancer(Router router, String name) {
        return collectBalancers(router).stream()
                .filter(lbi -> lbi.getName() != null && lbi.getName().equalsIgnoreCase(name))
                .map(LoadBalancingInterceptor::getClusterManager)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("balancer with name %s not found.".formatted(name)));
    }

    public static LoadBalancingInterceptor lookupBalancerInterceptor(Router router, String name) {
        return collectBalancers(router).stream()
                .filter(lbi -> lbi.getName() != null && lbi.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("balancer with name %s not found.".formatted(name)));
    }

    public static boolean hasLoadBalancing(Router router) {
        for (Proxy r : router.getRuleManager().getRules()) {
            List<Interceptor> interceptors = r.getFlow();
            if (interceptors == null)
                continue;
            for (Interceptor i : interceptors)
                if (i instanceof LoadBalancingInterceptor)
                    return true;
        }
        return false;
    }

    public static void up(Router router, String balancerName, String cName, String host, int port) {
        lookupBalancer(router, balancerName).up(cName, host, port);
    }

    public static void down(Router router, String balancerName, String cName, String host, int port) {
        lookupBalancer(router, balancerName).down(cName, host, port);
    }

    public static void takeout(Router router, String balancerName, String cName, String host, int port) {
        lookupBalancer(router, balancerName).takeout(cName, host, port);
    }

    public static List<Node> getAllNodesByCluster(Router router, String balancerName, String cName) {
        return lookupBalancer(router, balancerName).getAllNodesByCluster(cName);
    }

    public static List<Node> getAvailableNodesByCluster(Router router, String balancerName, String cName) {
        return lookupBalancer(router, balancerName).getAvailableNodesByCluster(cName);
    }

    public static void addSession2Cluster(Router router, String balancerName, String sessionId, String cName, Node n) {
        lookupBalancer(router, balancerName).addSession2Cluster(sessionId, cName, n);
    }

    public static void removeNode(Router router, String balancerName, String cluster, String host, int port) {
        lookupBalancer(router, balancerName).removeNode(cluster, host, port);
    }

    public static Node getNode(Router router, String balancerName, String cluster, String host, int port) {
        return lookupBalancer(router, balancerName).getNode(cluster, host, port);
    }

    public static Map<String, Session> getSessions(Router router, String balancerName, String cluster) {
        return lookupBalancer(router, balancerName).getSessions(cluster);
    }

    public static List<Session> getSessionsByNode(Router router, String balancerName, String cName, Node node) {
        return lookupBalancer(router, balancerName).getSessionsByNode(cName, node);
    }

    public static String getSingleClusterNameOrDefault(Balancer balancer) {
        if (balancer.getClusters().size() == 1)
            return balancer.getClusters().getFirst().getName();
        return Cluster.DEFAULT_NAME;
    }

}
