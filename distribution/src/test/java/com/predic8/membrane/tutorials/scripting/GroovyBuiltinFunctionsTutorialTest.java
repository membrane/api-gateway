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
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;

public class GroovyBuiltinFunctionsTutorialTest extends AbstractScriptingTutorialTest {

    @Override
    protected String getTutorialYaml() {
        return "15-Groovy-Builtin-Functions.yaml";
    }

    @Test
    void showsBuiltinFunctions() {
        // @formatter:off
        given()
            .contentType(JSON)
            .body("""
                {"shop":"Tea & More","teas":[{"name":"Earl Grey"},{"name":"Assam"}]}""")
        .when()
            .post("http://localhost:2000")
        .then()
            .statusCode(200)
            .contentType(JSON)
            .body("isJSON", equalTo(true))
            .body("teas", contains("Earl Grey", "Assam"))
            .body("path", equalTo("/shops/Tea%20%26%20More"))
            .body("query", equalTo("shop=Tea+%26+More"))
            .body("authorization", equalTo("Basic YWxpY2U6c2VjcmV0"));
        // @formatter:on
    }
}
