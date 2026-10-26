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
import static org.junit.jupiter.api.Assertions.assertEquals;

public class MultipleBalancersTutorialTest extends AbstractLoadbalancingTutorialTest {

    private static final String SHOP = "http://localhost:2000/shop";
    private static final String BILLING = "http://localhost:2000/billing";

    @Override
    protected String getTutorialYaml() {
        return "50-Multiple-Balancers.yaml";
    }

    @Test
    void balancersAreManagedIndependently() {
        assertEquals(Set.of("Shop node 1", "Shop node 2"), callTimes(SHOP, 2));
        assertEquals(Set.of("Billing node 1", "Billing node 2"), callTimes(BILLING, 2));

        get("http://localhost:9000/clustermanager/down?balancer=shop&host=localhost&port=3002").then().statusCode(204);

        assertEquals(Set.of("Shop node 1"), callTimes(SHOP, 4));
        assertEquals(Set.of("Billing node 1", "Billing node 2"), callTimes(BILLING, 2));
    }
}
