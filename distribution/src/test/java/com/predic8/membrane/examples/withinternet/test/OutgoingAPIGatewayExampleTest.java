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

package com.predic8.membrane.examples.withinternet.test;

import com.predic8.membrane.examples.util.DistributionExtractingTestcase;
import com.predic8.membrane.examples.util.Process2;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.Matchers.containsString;

public class OutgoingAPIGatewayExampleTest extends DistributionExtractingTestcase {

    @Override
    protected String getExampleDirName() {
        return "routing-traffic/outgoing-api-gateway";
    }

    @Test
    void test() throws Exception {
        try (Process2 ignored = startServiceProxyScript()) {
            // @formatter:off
            given()
                    .header("X-Api-Key", "10430")
                    .header("User-Agent", "secret")
                    .header("Authorization", "secret")
            .when()
                    .get("http://localhost:2000")
            .then()
                    .statusCode(200)
                    .body(containsString("X-Api-Key"))
                    .body(not(containsString("User-Agent")))
                    .body(not(containsString("Authorization")));
            // @formatter:on
        }
    }
}

