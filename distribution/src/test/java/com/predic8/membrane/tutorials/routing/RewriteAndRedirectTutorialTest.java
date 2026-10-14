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

import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

public class RewriteAndRedirectTutorialTest extends AbstractRoutingTutorialTest {

    @Override
    protected String getTutorialYaml() {
        return "10-Rewrite-and-Redirect.yaml";
    }

    @Test
    void rewritePath() {
        // @formatter:off
        given()
        .when()
            .get("http://localhost:2000/store/products/1")
        .then()
            .statusCode(200)
            .body("id", equalTo(1))
            .body("name", equalTo("Banana"));
        // @formatter:on
    }

    @Test
    void rewriteQueryString() {
        // @formatter:off
        given()
        .when()
            .get("http://localhost:2000/store/products?max=1")
        .then()
            .statusCode(200)
            .body("meta.limit", equalTo(1));
        // @formatter:on
    }

    @Test
    void redirectPermanently() {
        // @formatter:off
        given()
            .redirects().follow(false)
        .when()
            .get("http://localhost:2000/v1/products/1")
        .then()
            .statusCode(301)
            .header("Location", "/store/products/1");
        // @formatter:on
    }

    @Test
    void redirectTemporarily() {
        // @formatter:off
        given()
            .redirects().follow(false)
        .when()
            .get("http://localhost:2000/docs")
        .then()
            .statusCode(307)
            .header("Location", "https://www.membrane-api.io/docs/");
        // @formatter:on
    }

    @Test
    void routeToAnotherBackend() {
        // @formatter:off
        given()
        .when()
            .get("http://localhost:2000/legacy/orders")
        .then()
            .statusCode(200)
            .body(equalTo("Legacy backend received /orders"));
        // @formatter:on
    }
}
