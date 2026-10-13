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

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

/**
 * Verifies tutorial step 31-Access-Log-Custom-Fields.yaml: the header and the jsonPath() field from additionalPatternList appear in the access log line.
 */
public class AccessLogCustomFieldsTutorialTest extends AbstractLoggingTutorialTest {

    @Override
    protected String getTutorialYaml() {
        return "31-Access-Log-Custom-Fields.yaml";
    }

    @Override
    protected String getLoggingConfig() {
        return "log4j2-31-access-custom.yaml";
    }

    @Test
    void customFieldsAreLogged() throws Exception {
        given()
            .contentType("application/json")
            .body("{\"orderId\":\"A-1042\",\"items\":3}")
        .when()
            .post("http://localhost:2000/shop/orders")
        .then()
            .statusCode(200);

        var log = readLogFileStartingWith("access-custom-");
        assertThat(log, containsString("\"POST /shop/orders HTTP/1.1\" 200"));
        // REST Assured may append a charset to the Content-Type, so only its start is checked
        assertThat(log, containsString("[application/json"));
        assertThat(log, containsString("order=A-1042"));
    }
}
