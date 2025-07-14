package com.predic8.membrane.core.interceptor.balancer;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.config.AbstractXmlElement;
import com.predic8.membrane.core.exchange.AbstractExchange;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class PriorityStrategy extends AbstractXmlElement implements DispatchingStrategy {

    @Override
    public void init(Router router) {}

    @Override
    public Node dispatch(LoadBalancingInterceptor interceptor, AbstractExchange exc) throws EmptyNodeListException {
        List<Node> endpoints = interceptor.getEndpoints();
        if (endpoints.isEmpty()) {
            throw new EmptyNodeListException();
        }
        endpoints.sort(Comparator.comparingInt(Node::getPriority));

        Map<Integer, List<Node>> byPriority = endpoints.stream()
                .collect(Collectors.groupingBy(
                        Node::getPriority,
                        TreeMap::new,
                        Collectors.toList()
                ));

        for (List<Node> group : byPriority.values()) {
            List<Node> up = group.stream()
                    .filter(n -> n.getStatus() == Node.Status.UP)
                    .toList();
            if (!up.isEmpty()) {
                if (up.size() == 1) {
                    return up.getFirst();
                }
                return up.get(ThreadLocalRandom.current().nextInt(up.size()));
            }
        }

        // fallback: no UP nodes at any priority
        // return the first in the lowest-priority group
        return endpoints.getFirst();
    }


    @Override
    public void done(AbstractExchange exc) {}
}
