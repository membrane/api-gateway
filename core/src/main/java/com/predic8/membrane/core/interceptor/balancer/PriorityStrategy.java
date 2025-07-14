package com.predic8.membrane.core.interceptor.balancer;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.config.AbstractXmlElement;
import com.predic8.membrane.core.exchange.AbstractExchange;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import static com.predic8.membrane.core.interceptor.balancer.Node.Status.UP;
import static java.util.Comparator.comparingInt;


/**
 * @description Dispatch strategy that selects cluster nodes based on ascending priority and health.
 * Nodes are grouped by ascending priority.
 * The highest-priority group with one or more healthy nodes (status UP) is chosen.
 * If multiple nodes are healthy at that priority, one is selected at random.
 * If no nodes are UP, falls back to the first node in sorted order.
 */
public class PriorityStrategy extends AbstractXmlElement implements DispatchingStrategy {

    private static final Logger log = LoggerFactory.getLogger(PriorityStrategy.class);

    @Override
    public void init(Router router) {}

    @Override
    public Node dispatch(LoadBalancingInterceptor interceptor, AbstractExchange exc) throws EmptyNodeListException {
        List<Node> endpoints = interceptor.getEndpoints();
        if (endpoints.isEmpty()) {
            throw new EmptyNodeListException();
        }
        log.debug("Dispatching endpoints: {}", endpoints);
        endpoints.sort(comparingInt(Node::getPriority));

        for (List<Node> group : groupByPriority(endpoints).values()) {
            List<Node> up = getUpNodes(group);
            log.debug("Priority {}: {} nodes up out of {}", group.getFirst().getPriority(), up.size(), group.size());
            if (up.isEmpty())
                continue;

            if (up.size() == 1) {
                return up.getFirst();
            }
            return up.get(ThreadLocalRandom.current().nextInt(up.size()));

        }
        Node fallback = endpoints.getFirst();
        log.error("No UP nodes found in any priority group, falling back to {}", fallback);
        return fallback;
    }

    private static @NotNull List<Node> getUpNodes(List<Node> group) {
        return group.stream()
                .filter(n -> n.getStatus() == UP)
                .toList();
    }

    static @NotNull TreeMap<Integer, List<Node>> groupByPriority(List<Node> endpoints) {
        return endpoints.stream()
                .collect(Collectors.groupingBy(
                        Node::getPriority,
                        TreeMap::new,
                        Collectors.toList()
                ));
    }

    @Override
    public void done(AbstractExchange exc) {}
}
