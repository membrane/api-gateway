/* Copyright 2025 predic8 GmbH, www.predic8.com

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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.TreeMap;

import static com.predic8.membrane.core.interceptor.balancer.Node.Status.DOWN;
import static com.predic8.membrane.core.interceptor.balancer.Node.Status.UP;
import static org.junit.jupiter.api.Assertions.*;

class PriorityStrategyTest {

    private PriorityStrategy strategy;

    @BeforeEach
    public void setUp() {
        strategy = new PriorityStrategy();
    }

    @Test
    public void strategy() throws Exception {
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
        n1.setStatus(DOWN);
        assertSame(n3, strategy.dispatch(interceptor, null));
    }

    @Test
    void groupByPriority() {
        Node n1 = new Node("n1", 1);
        Node n2 = new Node("n2", 2);
        Node n3 = new Node("n3", 3);
        n1.setPriority(2);
        n2.setPriority(1);
        n3.setPriority(2);

        TreeMap<Integer, List<Node>> map = PriorityStrategy.groupByPriority(List.of(n1, n2, n3));

        assertEquals(2, map.size());
        assertTrue(map.containsKey(1));
        assertTrue(map.containsKey(2));

        List<Node> p1 = map.get(1);
        List<Node> p2 = map.get(2);

        assertEquals(List.of(n2), p1);
        assertTrue(p2.containsAll(List.of(n1, n3)));
    }

}
