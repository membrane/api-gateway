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

public class SetHeaderTutorialTest extends AbstractGettingStartedTutorialTest{

    @Override
    protected String getTutorialYaml() {
        return "60-SetHeader.yaml";
    }

    @Test
    void get() {
        // @formatter:off
        given()
        .when()
            .get("http://localhost:2000")
        .then()
            .statusCode(200)
            .header("X-Powered-By", equalTo("Membrane"))
            .header("X-Method", equalTo("GET"));
        // @formatter:on
    }

    @Test
    void post() {
        // @formatter:off
        given()
        .when()
            .post("http://localhost:2000")
        .then()
            .statusCode(200)
            .header("X-Powered-By", equalTo("Membrane"))
            .header("X-Method", equalTo("POST"));
        // @formatter:on
    }

}
