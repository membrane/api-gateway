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

package com.predic8.membrane.tutorials.transformation;

import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class TemplateJsonFromMapTutorialTest extends AbstractTransformationTutorialTest {

    @Override
    protected String getTutorialYaml() {
        return "30-Template-JSON-from-MAP.yaml";
    }

    @Test
    void responseMap() {
        // @formatter:off
        given()
        .when()
            .get("http://localhost:2000")
        .then()
            .statusCode(200)
            .contentType(containsString("application/json"))
            .body("Host", not(empty()))
            .body("Accept", notNullValue())
            .body("User-Agent", not(empty()))
            .body("X-Forwarded-For", not(empty()))
            .body("X-Forwarded-Proto", anyOf(equalTo("http"), equalTo("https")))
            .body("X-Forwarded-Host", not(empty()));
        // @formatter:on
    }

}
