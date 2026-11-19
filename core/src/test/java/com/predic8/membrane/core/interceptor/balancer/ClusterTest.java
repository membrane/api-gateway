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
package com.predic8.membrane.core.interceptor.balancer;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;

class ClusterTest {

    @Test
    void addNodeAddsAnUpNodeOnce() {
        final var cluster = new Cluster("c");

        assertTrue(cluster.addNode(new Node("backend.example.com", 8080)));
        assertFalse(cluster.addNode(new Node("backend.example.com", 8080)));

        assertEquals(1, cluster.getNodes().size());
        assertTrue(cluster.getNodes().getFirst().isUp());
    }

    @Test
    void addNodeLeavesAnExistingNodeUnchanged() {
        final var cluster = new Cluster("c");
        cluster.nodeDown(new Node("backend.example.com", 8080));

        assertFalse(cluster.addNode(new Node("backend.example.com", 8080)));

        assertFalse(cluster.getNodes().getFirst().isUp());
    }

    @Test
    void setExistingNodeStatusChangesOnlyExistingNodes() {
        final var cluster = new Cluster("c");
        cluster.addNode(new Node("backend.example.com", 8080));

        assertEquals(Node.Status.TAKEOUT, cluster.setExistingNodeStatus(new Node("backend.example.com", 8080), Node.Status.TAKEOUT).getStatus());
        assertNull(cluster.setExistingNodeStatus(new Node("other.example.com", 8080), Node.Status.UP));

        assertEquals(1, cluster.getNodes().size());
    }

    @Test
    void concurrentAddNodeAddsTheNodeOnce() throws Exception {
        final var cluster = new Cluster("c");
        final var threads = 16;
        final var start = new CountDownLatch(1);
        final var added = new AtomicInteger();
        try (final var executor = Executors.newFixedThreadPool(threads)) {
            final var results = new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for (var i = 0; i < threads; i++)
                results.add(executor.submit(() -> {
                    start.await();
                    if (cluster.addNode(new Node("backend.example.com", 8080)))
                        added.incrementAndGet();
                    return null;
                }));
            start.countDown();
            executor.shutdown();
            assertTrue(executor.awaitTermination(10, SECONDS));
            for (final var result : results)
                result.get(); // fails the test if a call threw
        }

        assertEquals(1, added.get());
        assertEquals(1, cluster.getNodes().size());
    }
}
