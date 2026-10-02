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

/**
 * Verifies tutorial step 30-Access-Log.yaml: accessLog writes a Common Log Format line into access-<start time>.log through log4j2-30-access.yaml.
 */
public class AccessLogTutorialTest extends AbstractLoggingTutorialTest {

    @Override
    protected String getTutorialYaml() {
        return "30-Access-Log.yaml";
    }

    @Override
    protected Map<String, String> getEnvs() {
        return Map.of("JAVA_OPTS", "-Dlog4j.configurationFile=tutorials/logging/log4j2-30-access.yaml");
    }

    @Test
    void accessLogLineIsWrittenToFile() throws Exception {
        when().get("http://localhost:2000/shop/products/7").then().statusCode(200);

        assertThat(readLogFileStartingWith("access-2"), containsString("\"GET /shop/products/7 HTTP/1.1\" 200 0"));
    }
}
