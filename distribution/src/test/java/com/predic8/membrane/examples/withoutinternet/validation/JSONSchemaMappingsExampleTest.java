/* Copyright 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.examples.withoutinternet.validation;

import com.predic8.membrane.examples.util.*;
import org.junit.jupiter.api.*;

import static com.predic8.membrane.core.http.MimeType.*;
import static io.restassured.RestAssured.*;
import static io.restassured.http.ContentType.*;
import static java.io.File.*;
import static org.hamcrest.Matchers.*;

public class JSONSchemaMappingsExampleTest extends DistributionExtractingTestcase {

    @Override
    protected String getExampleDirName() {
        return "validation" + separator + "json-schema" + separator + "schema-mappings";
    }

    @Test
    void port2000() throws Exception {
        try (Process2 ignored = startServiceProxyScript()) {

            // @formatter:off
            // Test good JSON
            given()
                .contentType(JSON)
                .body(readFileFromBaseDir("good2000.json"))
            .when()
                .post("http://localhost:2000")
                .then()
                .statusCode(200);

            // Test bad JSON
            given()
                .contentType(JSON)
                .body(readFileFromBaseDir("bad2000.json"))
            .when()
                .post("http://localhost:2000")
            .then()
                .statusCode(400)
                .contentType(APPLICATION_PROBLEM_JSON)
                .body("title", equalTo("JSON validation failed"))
                .body("type", equalTo("https://membrane-api.io/problems/user/validation"))
                .body("status", equalTo(400))
                .body("flow", equalTo("REQUEST"))

                // error 1: required value in param
                .body("errors.find { it.pointer == '/properties/param/$ref/required' }.key", equalTo("required"))
                .body("errors.find { it.pointer == '/properties/param/$ref/required' }.message", containsString("/param: required property 'value' not found"))
                .body("errors.find { it.pointer == '/properties/param/$ref/required' }.code", equalTo("1028"))

                // error 2: extra additional property
                .body("errors.find { it.pointer == '/additionalProperties' }.key", equalTo("additionalProperties"))
                .body("errors.find { it.pointer == '/additionalProperties' }.message", containsString("property 'extra' is not defined in the schema"))
                .body("errors.find { it.pointer == '/additionalProperties' }.code", equalTo("1001"))

                // meta fields
                .body("see", equalTo("https://membrane-api.io/problems/user/validation/json-schema-validator"))
                .body("attention", containsString("development mode"));
            // @formatter:on
        }
    }

    @Test
    void port2001() throws Exception {
        try (Process2 ignored = startServiceProxyScript()) {

            // @formatter:off
            // Test good JSON
            given()
                    .contentType(JSON)
                    .body(readFileFromBaseDir("good2001.json"))
                    .when()
                    .post("http://localhost:2001")
                    .then()
                    .statusCode(200);

            // Test bad JSON
            given()
                .contentType(JSON)
                .body(readFileFromBaseDir("bad2001.json"))
            .when()
                .post("http://localhost:2001")
            .then()
                .statusCode(400)
                .contentType(APPLICATION_PROBLEM_JSON)
                .body("title", equalTo("JSON validation failed"))
                .body("type", equalTo("https://membrane-api.io/problems/user/validation"))
                .body("status", equalTo(400))
                .body("flow", equalTo("REQUEST"))

                // error 1: unexpected additional property in params[0]
                .body("errors.find { it.pointer == '/properties/params/items/$ref/additionalProperties' }.key", equalTo("additionalProperties"))
                .body("errors.find { it.pointer == '/properties/params/items/$ref/additionalProperties' }.message", containsString("/params/0: property 'unexpected' is not defined in the schema"))
                .body("errors.find { it.pointer == '/properties/params/items/$ref/additionalProperties' }.code", equalTo("1001"))

                // error 2: meta.source minLength
                .body("errors.find { it.pointer == '/properties/meta/$ref/properties/source/minLength' }.key", equalTo("minLength"))
                .body("errors.find { it.pointer == '/properties/meta/$ref/properties/source/minLength' }.message", containsString("/meta/source: must be at least 1 characters long"))
                .body("errors.find { it.pointer == '/properties/meta/$ref/properties/source/minLength' }.code", equalTo("1017"))

                // error 3: meta.requestId required
                .body("errors.find { it.pointer == '/properties/meta/$ref/required' }.key", equalTo("required"))
                .body("errors.find { it.pointer == '/properties/meta/$ref/required' }.message", containsString("/meta: required property 'requestId' not found"))
                .body("errors.find { it.pointer == '/properties/meta/$ref/required' }.code", equalTo("1028"))

                // meta fields
                .body("see", equalTo("https://membrane-api.io/problems/user/validation/json-schema-validator"))
                .body("attention", containsString("development mode"));
            // @formatter:on
        }
    }
}
