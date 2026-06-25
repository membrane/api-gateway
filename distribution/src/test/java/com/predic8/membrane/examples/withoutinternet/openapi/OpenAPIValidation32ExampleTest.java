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

package com.predic8.membrane.examples.withoutinternet.openapi;

import com.predic8.membrane.examples.util.AbstractSampleMembraneStartStopTestcase;
import io.restassured.config.RestAssuredConfig;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.config;
import static io.restassured.RestAssured.given;
import static io.restassured.config.EncoderConfig.encoderConfig;
import static org.hamcrest.CoreMatchers.containsString;

public class OpenAPIValidation32ExampleTest extends AbstractSampleMembraneStartStopTestcase {

	// RestAssured does not know the application/jsonl content type; send its body verbatim as text.
	final RestAssuredConfig jsonlConfig = config().encoderConfig(
			encoderConfig().encodeContentTypeAs("application/jsonl", ContentType.TEXT));

	// JSON Lines streams: each line is one document validated against itemSchema (OpenAPI 3.2).
	final String validStream = "{\"id\": \"1\", \"title\": \"First\"}\n{\"id\": \"2\", \"title\": \"Second\"}";

	// The second item misses the required "title".
	final String invalidStream = "{\"id\": \"1\", \"title\": \"First\"}\n{\"id\": \"2\"}";

	@Override
	protected String getExampleDirName() {
		return "openapi/validation-3.2";
	}

	@Test
	public void validJsonLinesStreamIsForwarded() {
		// @formatter:off
		given()
			.config(jsonlConfig)
			.contentType("application/jsonl")
			.body(validStream)
		.when()
			.post(LOCALHOST_2000 + "/documents")
		.then()
			.statusCode(202)
			.body(containsString("success"));
		// @formatter:on
	}

	@Test
	public void streamItemViolatingItemSchemaIsRejected() {
		// @formatter:off
		given()
			.config(jsonlConfig)
			.contentType("application/jsonl")
			.body(invalidStream)
		.when()
			.post(LOCALHOST_2000 + "/documents")
		.then()
			.statusCode(400)
			.body(containsString("REQUEST/BODY#/1/title"))
			.body(containsString("Required property title is missing."));
		// @formatter:on
	}

	@Test
	public void queryMethodWithValidBodyIsForwarded() {
		// The OpenAPI 3.2 QUERY method carries a request body.
		// @formatter:off
		given()
			.contentType("application/json")
			.body("{\"term\": \"membrane\"}")
		.when()
			.request("QUERY", LOCALHOST_2000 + "/documents")
		.then()
			.statusCode(202)
			.body(containsString("success"));
		// @formatter:on
	}

	@Test
	public void queryMethodWithInvalidBodyIsRejected() {
		// @formatter:off
		given()
			.contentType("application/json")
			.body("{}")
		.when()
			.request("QUERY", LOCALHOST_2000 + "/documents")
		.then()
			.statusCode(400)
			.body(containsString("Required property term is missing."));
		// @formatter:on
	}

	@Test
	public void querystringParameterWithValidQueryIsForwarded() {
		// The OpenAPI 3.2 `in: querystring` parameter types the whole query string.
		// @formatter:off
		given()
		.when()
			.get(LOCALHOST_2000 + "/find?term=membrane&page=2")
		.then()
			.statusCode(200)
			.body(containsString("success"));
		// @formatter:on
	}

	@Test
	public void querystringParameterMissingRequiredIsRejected() {
		// @formatter:off
		given()
		.when()
			.get(LOCALHOST_2000 + "/find?page=2")
		.then()
			.statusCode(400)
			.body(containsString("term"));
		// @formatter:on
	}

	@Test
	public void xmlNodeTypeBodyIsValidated() {
		// id/currency as attributes and total value as element text — all via OpenAPI 3.2 xml.nodeType.
		// @formatter:off
		given()
			.contentType("application/xml")
			.body("<order id=\"A1\"><total currency=\"USD\">42.5</total></order>")
		.when()
			.post(LOCALHOST_2000 + "/orders")
		.then()
			.statusCode(201)
			.body(containsString("success"));
		// @formatter:on
	}

	@Test
	public void xmlNodeTypeTextWrongTypeIsRejected() {
		// The total value (nodeType: text, type number) is not a number.
		// @formatter:off
		given()
			.contentType("application/xml")
			.body("<order id=\"A1\"><total currency=\"USD\">not-a-number</total></order>")
		.when()
			.post(LOCALHOST_2000 + "/orders")
		.then()
			.statusCode(400);
		// @formatter:on
	}
}
