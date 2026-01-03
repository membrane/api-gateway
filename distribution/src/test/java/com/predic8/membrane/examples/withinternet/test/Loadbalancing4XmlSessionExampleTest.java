/* Copyright 2025 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.examples.withinternet.test;

import com.predic8.membrane.examples.util.DistributionExtractingTestcase;
import com.predic8.membrane.examples.util.Process2;
import org.junit.jupiter.api.Test;

import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;

public class Loadbalancing4XmlSessionExampleTest extends DistributionExtractingTestcase {

	private static final String BODY_SESSION_1 = """
			{"id":"SESSION1"}""";
	private static final String BODY_SESSION_2 = """
			{"id":"SESSION2"}""";

	@Override
	protected String getExampleDirName() {
		return "loadbalancing/4-session";
	}

	@Test
	void test() throws Exception {
		try(Process2 ignored = startServiceProxyScript()) {
            given().when()
                    .body(BODY_SESSION_1)
                    .contentType(APPLICATION_JSON)
                    .post("http://localhost:8080")
                .then()
                    .statusCode(200)
                    .body(containsString("Request count: 1"));

            given().when()
                    .body(BODY_SESSION_1)
                    .contentType(APPLICATION_JSON)
                    .post("http://localhost:8080")
                    .then()
                    .statusCode(200)
                    .body(containsString("Request count: 2"));

            given().when()
                    .body(BODY_SESSION_1)
                    .contentType(APPLICATION_JSON)
                    .post("http://localhost:8080")
                    .then()
                    .statusCode(200)
                    .body(containsString("Request count: 3"));

            given().when()
                    .body(BODY_SESSION_1)
                    .contentType(APPLICATION_JSON)
                    .post("http://localhost:8080")
                    .then()
                    .statusCode(200)
                    .body(containsString("Request count: 4"));

            given().when()
                    .body(BODY_SESSION_2)
                    .contentType(APPLICATION_JSON)
                    .post("http://localhost:8080")
                    .then()
                    .statusCode(200)
                    .body(containsString("Request count: 1"));

            given().when()
                    .body(BODY_SESSION_2)
                    .contentType(APPLICATION_JSON)
                    .post("http://localhost:8080")
                    .then()
                    .statusCode(200)
                    .body(containsString("Request count: 2"));

            given().when()
                    .body(BODY_SESSION_2)
                    .contentType(APPLICATION_JSON)
                    .post("http://localhost:8080")
                    .then()
                    .statusCode(200)
                    .body(containsString("Request count: 3"));

            given().when()
                    .body(BODY_SESSION_2)
                    .contentType(APPLICATION_JSON)
                    .post("http://localhost:8080")
                    .then()
                    .statusCode(200)
                    .body(containsString("Request count: 4"));
		}
	}
}
