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

import static io.restassured.RestAssured.get;
import static io.restassured.RestAssured.post;
import static org.hamcrest.Matchers.hasItems;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class DynamicNodesTutorialTest extends AbstractLoadbalancingTutorialTest {

    private static final String CLUSTER_MANAGER = "http://localhost:9000/clustermanager/%s?host=localhost&port=%d";

    @Override
    protected String getTutorialYaml() {
        return "20-Dynamic-Nodes.yaml";
    }

    @Test
    void registerAndRemoveNodes() {
        get("http://localhost:2000").then().statusCode(503);

        post(CLUSTER_MANAGER.formatted("up", 3001)).then().statusCode(204);
        post(CLUSTER_MANAGER.formatted("up", 3002)).then().statusCode(204);
        assertEquals(Set.of("Node 1", "Node 2"), callTimes("http://localhost:2000", 2));

        post(CLUSTER_MANAGER.formatted("down", 3001)).then().statusCode(204);
        assertEquals(Set.of("Node 2"), callTimes("http://localhost:2000", 4));

        // @formatter:off
        // the data of the load balancer page of the console
        get("http://localhost:9000/admin/api/v1/balancers")
        .then()
            .statusCode(200)
            .body("balancers[0].clusters[0].nodes.port", hasItems(3001, 3002));
        // @formatter:on
    }
}
