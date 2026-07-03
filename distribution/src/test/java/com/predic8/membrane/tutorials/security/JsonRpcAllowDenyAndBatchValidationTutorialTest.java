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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

public class JsonRpcAllowDenyAndBatchValidationTutorialTest extends AbstractSecurityTutorialTest {

    @Override
    protected String getTutorialDir() {
        return "security/json-rpc";
    }

    @Override
    protected String getTutorialYaml() {
        return "10-JSON-RPC-Allow-Deny-and-Batch-Validation.yaml";
    }

    @Test
    void allowsConfiguredMethod_ping() {
        // @formatter:off
        given()
            .contentType(JSON)
            .body("""
                {"jsonrpc":"2.0","id":1,"method":"ping"}
                """)
        .when()
            .post("http://localhost:2000")
        .then()
            .statusCode(200)
            .contentType(JSON)
            .body("jsonrpc", equalTo("2.0"))
            .body("id", equalTo(1))
            .body("result.ok", equalTo(true));
        // @formatter:on
    }

    @Test
    void rejectsMethodOutsideAllowlist() {
        // @formatter:off
        given()
            .contentType(JSON)
            .body("""
                {"jsonrpc":"2.0","id":1,"method":"shutdown"}
                """)
        .when()
            .post("http://localhost:2000")
        .then()
            .statusCode(403)
            .contentType(JSON)
            .body("jsonrpc", equalTo("2.0"))
            .body("id", equalTo(1))
            .body("error.code", equalTo(-32601))
            .body("error.message", containsString("shutdown"));
        // @formatter:on
    }

    @Test
    void acceptsBatchUpToMaxSize() {
        // @formatter:off
        given()
            .contentType(JSON)
            .body("""
                [
                  {"jsonrpc":"2.0","id":1,"method":"ping"},
                  {"jsonrpc":"2.0","id":2,"method":"sum","params":[1,2]}
                ]
                """)
        .when()
            .post("http://localhost:2000")
        .then()
            .statusCode(200)
            .contentType(JSON)
            .body("size()", equalTo(2))
            .body("[0].jsonrpc", equalTo("2.0"))
            .body("[0].id", equalTo(1))
            .body("[0].result.ok", equalTo(true))
            .body("[1].jsonrpc", equalTo("2.0"))
            .body("[1].id", equalTo(2))
            .body("[1].result.ok", equalTo(true));
        // @formatter:on
    }

    @Test
    void rejectsBatchThatExceedsMaxSize() {
        // @formatter:off
        given()
            .contentType(JSON)
            .body("""
                [
                  {"jsonrpc":"2.0","id":1,"method":"ping"},
                  {"jsonrpc":"2.0","id":2,"method":"sum","params":[1,2]},
                  {"jsonrpc":"2.0","id":3,"method":"ping"}
                ]
                """)
        .when()
            .post("http://localhost:2000")
        .then()
            .statusCode(400)
            .contentType(JSON)
            .body("size()", equalTo(1))
            .body("[0].jsonrpc", equalTo("2.0"))
            .body("[0].id", nullValue())
            .body("[0].error.code", equalTo(-32600))
            .body("[0].error.message", equalTo("Batch request exceeds maxSize of 2."));
        // @formatter:on
    }
}
