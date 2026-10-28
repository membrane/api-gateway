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
import static org.hamcrest.Matchers.equalTo;

public class JavaScriptBuiltinVariablesTutorialTest extends AbstractScriptingTutorialTest {

    @Override
    protected String getTutorialYaml() {
        return "70-JavaScript-Builtin-Variables.yaml";
    }

    @Test
    void showsBuiltinVariables() {
        // @formatter:off
        given()
            .contentType(JSON)
            .header("X-Customer", "42")
            .cookie("session", "abc")
            .body("""
                {"name":"Oolong"}""")
        .when()
            .post("http://localhost:2000/shop/tea?limit=10")
        .then()
            .statusCode(200)
            .contentType(JSON)
            .header("X-Flow", "RESPONSE")
            .header("X-Status-Code", "200")
            .body("flow", equalTo("REQUEST"))
            .body("method", equalTo("POST"))
            .body("path", equalTo("/shop/tea?limit=10"))
            .body("category", equalTo("tea"))
            .body("limit", equalTo("10"))
            .body("customer", equalTo("42"))
            .body("session", equalTo("abc"))
            .body("name", equalTo("Oolong"))
            .body("tenant", equalTo("demo"));
        // @formatter:on
    }
}
