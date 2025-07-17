package com.predic8.membrane.core.interceptor.balancer;

import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.*;
import com.predic8.membrane.core.config.*;
import com.predic8.membrane.core.exchange.*;
import org.jetbrains.annotations.*;
import org.slf4j.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.*;

import static com.predic8.membrane.core.interceptor.balancer.Node.Status.*;
import static java.util.Comparator.comparing;


/**
 * @description Dispatch strategy that selects cluster nodes based on ascending priority and health.
 * Nodes are grouped by ascending priority.
 * The highest-priority group with one or more healthy nodes (status UP) is chosen.
 * If multiple nodes are healthy at that priority, one is selected at random.
 * If no nodes are UP, falls back to the first node in sorted order.
 */
@MCElement(name="priorityStrategy")
public class PriorityStrategy extends AbstractXmlElement implements DispatchingStrategy {

    private static final Logger log = LoggerFactory.getLogger(PriorityStrategy.class);

    @Override
    public void init(Router router) {}

    @Override
    public Node dispatch(LoadBalancingInterceptor interceptor, AbstractExchange exc) throws EmptyNodeListException {
        for (List<Node> group : groupByPriority(getNodes(interceptor)).values()) {
            List<Node> up = getUpNodes(group);
            log.debug("Priority {}: {} nodes up out of {}", group.getFirst().getPriority(), up.size(), group.size());
            if (up.isEmpty())
                continue;
            if (up.size() == 1) {
                return up.getFirst();
            }
            return up.get(ThreadLocalRandom.current().nextInt(up.size()));
        }
        Node fallback = getNodes(interceptor).getFirst();
        log.error("No UP nodes found in any priority group, falling back to {}", fallback);
        return fallback;
    }

    private static @NotNull List<Node> getNodes(LoadBalancingInterceptor interceptor) throws EmptyNodeListException {
        List<Node> endpoints = interceptor.getEndpoints();
        if (endpoints.isEmpty()) {
            throw new EmptyNodeListException();
        }
        return endpoints;
    }

    private static @NotNull List<Node> getUpNodes(List<Node> group) {
        return group.stream()
                .filter(n -> n.getStatus() == UP)
                .toList();
    }

    static @NotNull TreeMap<Integer, List<Node>> groupByPriority(List<Node> endpoints) {
        return endpoints.stream()
                .sorted(comparing(Node::getPriority))
                .collect(Collectors.groupingBy(
                        Node::getPriority,
                        TreeMap::new,
                        Collectors.toList()
                ));
    }

    @Override
    public void done(AbstractExchange exc) {}
}
