/* Copyright 2024 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.examples.tests.validation;

import com.predic8.membrane.examples.util.*;
import org.junit.jupiter.api.*;

import java.io.*;

import static com.predic8.membrane.core.http.MimeType.*;
import static io.restassured.RestAssured.*;
import static java.io.File.*;

public class SOAPProxyValidationExampleTest extends DistributionExtractingTestcase {

    @Override
    protected String getExampleDirName() {
        return "validation" + separator + "soap-Proxy";
    }

    @Test
    public void testValidCitySoapRequest() throws IOException, InterruptedException {
        try (Process2 ignored = startServiceProxyScript()) {
            // @formatter:off
			given()
				.contentType(APPLICATION_SOAP)
				.body(readFile("city-soap.xml"))
			.when()
				.post("http://localhost:2000/")
			.then()
				.statusCode(200)
				.extract().asString();
			// @formatter:on
        }
    }

    @Test
    public void testInvalidCitySoapRequest() throws IOException, InterruptedException {
        try (Process2 ignored = startServiceProxyScript()) {
            // @formatter:off
			given()
				.contentType(APPLICATION_SOAP)
				.body(readFile("invalid-city-soap.xml"))
			.when()
				.post("http://localhost:2000/")
			.then()
				.log().ifValidationFails()
				.statusCode(200);
			// @formatter:on
        }
    }
}
