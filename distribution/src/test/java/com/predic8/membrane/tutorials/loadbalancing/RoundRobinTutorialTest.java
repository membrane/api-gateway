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

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RoundRobinTutorialTest extends AbstractLoadbalancingTutorialTest {

    @Override
    protected String getTutorialYaml() {
        return "10-Round-Robin.yaml";
    }

    @Test
    void threeCallsReachAllThreeNodes() {
        assertEquals(Set.of("Node 1", "Node 2", "Node 3"), callTimes("http://localhost:2000", 3));
    }
}
