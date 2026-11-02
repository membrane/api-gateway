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
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;

class GroovyCheckJsonTutorialTest extends AbstractJsonTutorialTest {

    @Override
    protected String getTutorialYaml() {
        return "50-Groovy-Check-JSON.yaml";
    }

    @Test
    void tooManyPiecesAreRejected() throws IOException {
        // @formatter:off
        given()
            .body(readFileFromBaseDir("order.json"))
            .contentType(JSON)
        .when()
            .post("http://localhost:2000")
        .then()
            .statusCode(400)
            .contentType(JSON)
            .body("error", equalTo("At most 10 pieces per article"))
            .body("articles", contains("Butter"));
        // @formatter:on
    }

    @Test
    void validOrderPasses() {
        // @formatter:off
        given()
            .body("""
                {"id":325,"items":[{"article":"Tea","quantity":2,"price":1.87}]}""")
            .contentType(JSON)
        .when()
            .post("http://localhost:2000")
        .then()
            .statusCode(200)
            .body("id", equalTo(325))
            .body("items.article", contains("Tea"));
        // @formatter:on
    }
}
