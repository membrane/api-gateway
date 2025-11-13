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

package com.predic8.membrane.tutorials.getting_started;

import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.core.IsNull.notNullValue;

public class BasicPathRoutingTutorialTest extends AbstractGettingStartedTutorialTest{

    @Override
    protected String getTutorialYaml() {
        return "40-Basic-Path-Routing.yaml";
    }

    @Test
    void callProducts() {
        // @formatter:off
        given()
        .when()
            .get("http://localhost:2000/shop/v2/products")
        .then()
            .statusCode(200)
            .body("meta.count", greaterThanOrEqualTo(0))
            .body("meta.start", greaterThanOrEqualTo(0))
            .body("meta.limit", greaterThan(0))
            .body("products.size()", greaterThan(0))
            .body("products[0].id", greaterThan(0));
        // @formatter:on
    }

    @Test
    void callCatFact() {
        // @formatter:off
        given()
        .when()
            .get("https://catfact.ninja/fact")
        .then()
            .statusCode(200)
            .body("fact", notNullValue())
            .body("length", greaterThan(0));
        // @formatter:on
    }

    @Test
    void callHttpbin() {
        // @formatter:off
        given()
        .when()
            .get("https://httpbin.org/get")
        .then()
            .statusCode(200)
            .body("url", equalTo("https://httpbin.org/get"))
            .body("headers", notNullValue())
            .body("origin", notNullValue());
        // @formatter:on
    }

}
