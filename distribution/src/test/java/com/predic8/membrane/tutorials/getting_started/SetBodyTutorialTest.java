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

package com.predic8.membrane.tutorials.getting_started;

import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class SetBodyTutorialTest extends AbstractGettingStartedTutorialTest {

    @Override
    protected String getTutorialYaml() {
        return "65-SetBody.yaml";
    }

    @Test
    void path_and_headers_list() {
        // @formatter:off
        given()
        .when()
            .get("http://localhost:2000/spel")
        .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body(containsString("Path: /spel"))
            .body(containsString("Host"));
        // @formatter:on
    }

    @Test
    void extract_city() {
        // @formatter:off
        given()
            .contentType("application/json")
            .body("{\"city\":\"Seoul\"}")
        .when()
            .post("http://localhost:2000/jsonpath")
        .then()
            .statusCode(200)
            .body(startsWith("City: Seoul"));
        // @formatter:on
    }

}
