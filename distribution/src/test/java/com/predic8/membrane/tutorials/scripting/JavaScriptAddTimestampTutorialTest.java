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

package com.predic8.membrane.tutorials.scripting;

import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.matchesPattern;

class JavaScriptAddTimestampTutorialTest extends AbstractScriptingTutorialTest {

    @Override
    protected String getTutorialYaml() {
        return "75-JavaScript-Add-Timestamp.yaml";
    }

    @Test
    void addsTimestamp() {
        // @formatter:off
        given()
            .contentType(JSON)
            .body("{}")
        .when()
            .post("http://localhost:2000")
        .then()
            .statusCode(200)
            .contentType(JSON)
            .body("timestamp", matchesPattern("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z"));
        // @formatter:on
    }
}
