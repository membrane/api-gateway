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

package com.predic8.membrane.tutorials.soap;

import org.junit.jupiter.api.*;

import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

public class RoutingBySOAPActionTutorialTest extends AbstractSOAPTutorialTest {

    @Override
    protected String getTutorialYaml() {
        return "75-Routing-by-SOAPAction.yaml";
    }

    @Test
    void routesToGetCityBySOAPAction() {
        // @formatter:off
        given()
            .header("SOAPAction", "https://predic8.de/city-service/get")
        .when()
            .post("http://localhost:2000")
        .then()
            .statusCode(200)
            .body(equalTo("getCity"));
        // @formatter:on
    }

    @Test
    void routesToCreateCityBySOAPAction() {
        // @formatter:off
        given()
            .header("SOAPAction", "https://predic8.de/city-service/create")
        .when()
            .post("http://localhost:2000")
        .then()
            .statusCode(200)
            .body(equalTo("createCity"));
        // @formatter:on
    }

    @Test
    void fallbackRouteForUnknownSOAPAction() {
        // @formatter:off
        given()
            .header("SOAPAction", "https://predic8.de/city-service/unknown")
        .when()
            .post("http://localhost:2000")
        .then()
            .statusCode(404);
        // @formatter:on
    }

}
