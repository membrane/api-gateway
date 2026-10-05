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

package com.predic8.membrane.tutorials.routing;

import com.predic8.membrane.examples.util.SubstringWaitableConsoleEvent;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

public class ShadowingTrafficTutorialTest extends AbstractRoutingTutorialTest {

    @Override
    protected String getTutorialYaml() {
        return "80-Shadowing-Traffic.yaml";
    }

    @Test
    void clientGetsProductionResponseAndShadowsGetCopies() throws Exception {
        final var shopV2 = new SubstringWaitableConsoleEvent(process, "Shop v2 received GET /products");
        final var analytics = new SubstringWaitableConsoleEvent(process, "Analytics received GET /products");

        // @formatter:off
        given()
        .when()
            .get("http://localhost:2000/products")
        .then()
            .statusCode(200)
            .body(equalTo("Production backend answered GET /products"));
        // @formatter:on

        shopV2.waitFor(5000);
        analytics.waitFor(5000);
    }
}
