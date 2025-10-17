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

public class JSONSchemaValidationExampleTest extends DistributionExtractingTestcase {

	@Override
	protected String getExampleDirName() {
		return "validation" + separator + "json-schema";
	}

    @Test
    void port2000() throws Exception {
        try(Process2 ignored = startServiceProxyScript()) {

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
                .body(containsString("p1"))
                .body("errors.find { it.pointer == '/required' }.message", containsString("not found"));
            // @formatter:on

        }
    }

    @Test
    void port2001() throws Exception {
        try(Process2 ignored = startServiceProxyScript()) {

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
                .body("errors.find { it.pointer == '/properties/id/type' }.message", containsString("integer expected"))
                .body("errors.find { it.pointer == '/properties/price/type' }.message", containsString("number expected"))
                .body("errors.find { it.pointer == '/properties/tags/type' }.message", containsString("array expected"))
                .body("errors.find { it.pointer == '/properties/weight/minimum' }.message", containsString("700"));
            // @formatter:on

        }
    }
}