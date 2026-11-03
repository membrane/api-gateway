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

package com.predic8.membrane.tutorials.json;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.*;

class GroovyModifyJsonTutorialTest extends AbstractJsonTutorialTest {

    @Override
    protected String getTutorialYaml() {
        return "40-Groovy-Modify-JSON.yaml";
    }

    @Test
    void orderIsModified() throws IOException {
        // @formatter:off
        given()
            .body(readFileFromBaseDir("order.json"))
            .contentType(JSON)
        .when()
            .post("http://localhost:2000")
        .then()
            .statusCode(200)
            .contentType(JSON)
            .body("$", not(hasKey("id")))
            .body("orderId", equalTo(324))
            .body("items.amount", contains(3.74f, 11.88f, 15.6f))
            .body("total", equalTo(31.22f))
            .body("currency", equalTo("EUR"));
        // @formatter:on
    }
}
