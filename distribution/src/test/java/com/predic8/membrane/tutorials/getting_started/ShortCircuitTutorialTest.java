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
import static org.hamcrest.CoreMatchers.containsString;

public class ShortCircuitTutorialTest extends AbstractGettingStartedTutorialTest{

    @Override
    protected String getTutorialYaml() {
        return "50-Short-Circuit.yaml";
    }

    @Test
    void statusCode() {
        // @formatter:off
        given()
        .when()
            .get("http://localhost:2000")
        .then()
            .statusCode(200);
        // @formatter:on
    }

    @Test
    void body() {
        // @formatter:off
        given()
            .body("Bonjour")
            .contentType("application/x-www-form-urlencoded")
        .when()
            .post("http://localhost:2000")
        .then()
            .statusCode(200)
            .body(containsString("Bonjour"));
        // @formatter:on
    }
}
