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

package com.predic8.membrane.tutorials.logging;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.when;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

/**
 * Verifies tutorial step 32-Access-Log-Per-Api.yaml: each API writes its access log into its own file.
 */
public class AccessLogPerApiTutorialTest extends AbstractLoggingTutorialTest {

    @Override
    protected String getTutorialYaml() {
        return "32-Access-Log-Per-Api.yaml";
    }

    @Override
    protected Map<String, String> getEnvs() {
        return Map.of("JAVA_OPTS", "-Dlog4j.configurationFile=tutorials/logging/log4j2-32-access-per-api.yaml");
    }

    @Test
    void eachApiLogsIntoItsOwnFile() throws Exception {
        when().get("http://localhost:2000/orders/1").then().statusCode(200);
        when().get("http://localhost:2000/products/7").then().statusCode(200);
        when().get("http://localhost:2000/users/alice").then().statusCode(200);

        assertThat(readFile("access-orders.log"), containsString("\"GET /orders/1 HTTP/1.1\" 200"));
        assertThat(readFile("access-users.log"), containsString("\"GET /users/alice HTTP/1.1\" 200"));

        var products = readFile("access-products.log");
        assertThat(products, containsString("\"GET /products/7 HTTP/1.1\" 200"));
        assertThat(products, not(containsString("/orders/1")));
    }
}
