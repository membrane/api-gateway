package com.predic8.membrane.core.interceptor.balancer;

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.config.*;
import com.predic8.membrane.core.exchange.*;
import org.jetbrains.annotations.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.*;

import static java.util.Comparator.*;

// log debug
public class PriorityStrategy extends AbstractXmlElement implements DispatchingStrategy {

    @Override
    public void init(Router router) {
    }

    @Override
    public Node dispatch(LoadBalancingInterceptor interceptor, AbstractExchange exc) throws EmptyNodeListException {
        List<Node> endpoints = interceptor.getEndpoints();
        if (endpoints.isEmpty()) {
            throw new EmptyNodeListException();
        }
        endpoints.sort(comparingInt(Node::getPriority));

        for (List<Node> group : groupByPriority(endpoints).values()) {
            List<Node> up = getUpNodes(group);
            if (up.isEmpty())
                continue;

            if (up.size() == 1) {
                return up.getFirst();
            }
            return up.get(ThreadLocalRandom.current().nextInt(up.size()));

        }

        // Todo: high, log
        // fallback: no UP nodes at any priority
        // return the first in the lowest-priority group
        return endpoints.getFirst();
    }

    private static @NotNull List<Node> getUpNodes(List<Node> group) {
        return group.stream()
                .filter(n -> n.getStatus() == Node.Status.UP)
                .toList();
    }

    // Test
    private static @NotNull TreeMap<Integer, List<Node>> groupByPriority(List<Node> endpoints) {
        return endpoints.stream()
                .collect(Collectors.groupingBy(
                        Node::getPriority,
                        TreeMap::new,
                        Collectors.toList()
                ));
    }

    @Override
    public void done(AbstractExchange exc) {
    }
}
