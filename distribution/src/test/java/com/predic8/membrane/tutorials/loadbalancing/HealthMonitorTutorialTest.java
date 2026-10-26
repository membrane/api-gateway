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

package com.predic8.membrane.tutorials.loadbalancing;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.fail;

public class HealthMonitorTutorialTest extends AbstractLoadbalancingTutorialTest {

    @Override
    protected String getTutorialYaml() {
        return "40-Health-Monitor.yaml";
    }

    @Test
    void flappingNodeIsTakenOutAndPutBack() throws InterruptedException {
        // The first health check runs 5 seconds after startup, then node 2 switches every 10 seconds.
        awaitNodes(Set.of("Node 1"), "Node 2 was not taken out of service.");
        assertThat(logger.toString(), containsString("Node localhost:3002 health check failed with HTTP 500 status code"));
        awaitNodes(Set.of("Node 1", "Node 2"), "Node 2 was not put back into service.");
    }

    private static void awaitNodes(Set<String> expected, String message) throws InterruptedException {
        final var deadline = System.currentTimeMillis() + 30_000;
        while (!callTimes("http://localhost:2000", 4).equals(expected)) {
            if (System.currentTimeMillis() > deadline)
                fail(message);
            Thread.sleep(500);
        }
    }
}
