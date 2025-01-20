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

package com.predic8.membrane.examples.tests.openapi;

import com.predic8.membrane.examples.util.*;
import org.junit.jupiter.api.*;
import org.skyscreamer.jsonassert.*;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.CoreMatchers.containsString;

public class OpenAPIValidationSimpleExampleTest extends DistributionExtractingTestcase {

	final String createPersonValid = """
			{"name": "Johannes Gutenberg","age": 78}
			""";

	final String createPersonInvalid = """
			{"name": "Johannes Gutenberg","age": -10}
			""";

	final String validationResult = """
				{
					"title": "OpenAPI message validation failed",
					"type": "https://membrane-api.io/error/user/openapi/validation",
					"validation": {
				 		"method" : "POST",
				  		"uriTemplate" : "/persons",
				  		"path" : "/persons",
				  		"errors" : {
							"REQUEST/BODY#/age" : [ {
					  		"message" : "-10 is smaller than the minimum of 0",
					  		"complexType" : "Person",
					  		"schemaType" : "integer"
						}]
				  	}
				  }
				}""";

	@Override
	protected String getExampleDirName() {
		return "openapi/validation-simple";
	}

	@Test
	public void test() throws Exception {

		try(Process2 ignored = startServiceProxyScript()) {
			// @formatter:off
			// Test valid person creation
			given()
				.contentType(JSON)
				.body(createPersonValid)
			.when()
				.post(LOCALHOST_2000 + "/persons")
			.then()
				.statusCode(201)
				.body(containsString("success"));

			// Test invalid person creation
			String response =given()
				.contentType(JSON)
				.body(createPersonInvalid)
			.when()
				.post(LOCALHOST_2000 + "/persons")
			.then()
				.statusCode(400)
				.extract().response().asString();
			// @formatter:on

			JSONAssert.assertEquals(validationResult, response, false);
		}
	}
}
