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

package com.predic8.membrane.tutorials.rate_limiting;

import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;

class GlobalRateLimitTutorialTest extends AbstractRateLimitingTutorialTest {

    @Override
    protected String getTutorialYaml() {
        return "20-Global-Rate-Limit.yaml";
    }

    @Test
    void apiLimitIsStricterAndGlobalLimitCountsAllApis() {
        // @formatter:off
        given()
        .when()
            .get("http://localhost:2000/products")
        .then()
            .statusCode(200)
            .header("X-RateLimit-Limit", "10")
            .header("X-RateLimit-Remaining", "9");

        given()
        .when()
            .post("http://localhost:2000/login")
        .then()
            .statusCode(200)
            .header("X-RateLimit-Limit", "2")
            .header("X-RateLimit-Remaining", "1");

        given()
        .when()
            .post("http://localhost:2000/login")
        .then()
            .statusCode(200);

        given()
        .when()
            .post("http://localhost:2000/login")
        .then()
            .statusCode(429)
            .header("X-RateLimit-Limit", "2")
            .header("X-RateLimit-Remaining", "0");

        given()
        .when()
            .get("http://localhost:2000/products")
        .then()
            .statusCode(200)
            .header("X-RateLimit-Limit", "10")
            .header("X-RateLimit-Remaining", "5");
        // @formatter:on
    }
}
