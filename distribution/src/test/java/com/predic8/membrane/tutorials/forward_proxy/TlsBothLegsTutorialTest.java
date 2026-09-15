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

package com.predic8.membrane.tutorials.forward_proxy;

import io.restassured.specification.ProxySpecification;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;

public class TlsBothLegsTutorialTest extends AbstractForwardProxyTutorialTest {

    @Override
    protected String getTutorialYaml() {
        return "20-TLS-Both-Legs.yaml";
    }

    @Test
    void tunnelsHttpsRequestToTheRealBackendOverAnHttpsProxy() {
        // @formatter:off
        given()
            .proxy(ProxySpecification.host("localhost").withPort(8443).withScheme("https"))
            .relaxedHTTPSValidation()
        .when()
            .get("https://api.predic8.de/")
        .then()
            .statusCode(200)
            .body(containsString("Shop API Showcase"));
        // @formatter:on
    }
}
