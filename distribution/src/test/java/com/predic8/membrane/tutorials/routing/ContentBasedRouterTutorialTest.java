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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.equalTo;

public class ContentBasedRouterTutorialTest extends AbstractRoutingTutorialTest {

    @Override
    protected String getTutorialYaml() {
        return "30-Content-Based-Router.yaml";
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "{\"id\": 1, \"express\": true}                       | Express order received.",
            "{\"id\": 2, \"shipping\": {\"international\": true}} | International order received.",
            "{\"id\": 3}                                          | Normal order received.",
            "{\"id\": 4, \"express\": false}                      | Normal order received."
    })
    void routesByJsonContent(String order, String expected) {
        // @formatter:off
        given()
            .contentType(JSON)
            .body(order)
        .when()
            .post("http://localhost:2000/orders")
        .then()
            .statusCode(200)
            .body(equalTo(expected));
        // @formatter:on
    }
}
