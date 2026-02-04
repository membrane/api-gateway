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

package com.predic8.membrane.tutorials.security;

import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.equalTo;

public class JsonProtectionTutorialTest extends AbstractSecurityTutorialTest {

    @Override
    protected String getTutorialYaml() {
        return "80-JSON-Protection.yaml";
    }

    @Test
    void rejectsAllSamplePayloads() {
        assertRejected("{\"a\":{\"b\":{\"c\":{\"d\":1}}}}"); // Depth
        assertRejected("{\"abcdefghijk\": 0 }"); // Key Length
        assertRejected("{\"a\":1,\"b\":2,\"c\":3,\"d\":4}"); // Object Size
        assertRejected("{\"a\":[1,2],\"b\":[1,2],\"c\":[1,2]}"); // Number of Tokens
        assertRejected("{\"a\": \"12345678901\" }"); // String Length
        assertRejected("{\"a\":[1,2,3,4,5,6,7,8,9,10]}"); // Array Size
        assertRejected("{\"__proto__\":{\"a\":1}}"); // Prototype Pollution
        assertRejected("{\"a\":1,\"a\":2}"); // Duplicate Key
    }

    private void assertRejected(String payload) {
        // @formatter:off
        given()
            .contentType(JSON)
            .body(payload)
        .when()
            .post("http://localhost:2000")
        .then()
            .statusCode(400)
            .body("title", equalTo("JSON protection"));
        // @formatter:on
    }
}
