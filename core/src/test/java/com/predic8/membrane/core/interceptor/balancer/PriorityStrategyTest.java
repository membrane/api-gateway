package com.predic8.membrane.core.interceptor.balancer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static com.predic8.membrane.core.interceptor.balancer.Node.Status.DOWN;
import static com.predic8.membrane.core.interceptor.balancer.Node.Status.UP;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PriorityStrategyTest {

    private PriorityStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new PriorityStrategy();
    }

    @Test
    void priority1() throws Exception {
        Node n1 = new Node("host1", 1);
        n1.setStatus(UP);
        n1.setPriority(1);

        Node n2 = new Node("host2", 2);
        n2.setStatus(UP);
        n2.setPriority(1);

        Node n3 = new Node("host3", 3);
        n3.setStatus(UP);
        n3.setPriority(2);

        LoadBalancingInterceptor interceptor = new LoadBalancingInterceptor() {
            @Override
            public List<Node> getEndpoints() {
                return Arrays.asList(n1, n2, n3);
            }
        };
        Node res = strategy.dispatch(interceptor, null);
        assertTrue(res.equals(n1) || res.equals(n2));
        n1.setStatus(DOWN);
        assertSame(n2, strategy.dispatch(interceptor, null));
        n2.setStatus(DOWN);
        n1.setStatus(UP);
        assertSame(n1, strategy.dispatch(interceptor, null));
    }

    @Test
    void priority2() throws Exception {
        Node n1 = new Node("host1", 1);
        n1.setStatus(DOWN);
        n1.setPriority(1);

        Node n2 = new Node("host2", 2);
        n2.setStatus(DOWN);
        n2.setPriority(1);

        Node n3 = new Node("host3", 3);
        n3.setStatus(UP);
        n3.setPriority(2);

        LoadBalancingInterceptor interceptor = new LoadBalancingInterceptor() {
            @Override
            public List<Node> getEndpoints() {
                return Arrays.asList(n1, n2, n3);
            }
        };

        Node result = strategy.dispatch(interceptor, null);
        assertSame(n3, result);
    }
}
