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

package com.predic8.membrane.tutorials.orchestration;

import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

public class OrchestrationTutorialTest extends AbstractOrchestrationTutorialTest {

    @Override
    protected String getTutorialYaml() {
        return "10-Orchestration.yaml";
    }

    @Test
    void books_areReturnedById() {
        // @formatter:off
        given()
        .when()
            .get("http://localhost:2000/books/{id}", "OL29474405M")
        .then()
            .statusCode(200)
            .body("title", equalTo("So Long, and Thanks for All the Fish"))
            .body("author", equalTo("Douglas Adams"));

        given()
        .when()
            .get("http://localhost:2000/books/{id}", "OL26333978M")
        .then()
            .statusCode(200)
            .body("title", equalTo("Foucault's pendulum"))
            .body("author", equalTo("Umberto Eco"));
        // @formatter:on
    }

}
