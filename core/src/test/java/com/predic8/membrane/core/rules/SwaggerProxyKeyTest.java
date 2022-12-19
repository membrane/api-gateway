/* Copyright 2009, 2011, 2015 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.rules;

import static org.junit.jupiter.api.Assertions.*;

import com.predic8.membrane.core.interceptor.swagger.SwaggerAdapter;
import io.swagger.parser.SwaggerParser;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class SwaggerProxyKeyTest {

	static SwaggerProxyKey key;

	@BeforeAll
	public static void oneTimeSetUp() {
		key = new SwaggerProxyKey(3000);
		key.setSwagger(new SwaggerAdapter(new SwaggerParser().read("http://petstore.swagger.io/v2/swagger.json")));
	}

	@Test
	// This test might fail if the online petstore specification is changed for some reason.
	public void testParsedSwagger() throws Exception {
		//assertEquals("2.0", key.getSwagger().getSwagger());
		assertEquals("/v2", key.getSwagger().getBasePath());
		//assertEquals("Swagger Petstore", key.getSwagger().getInfo().getTitle());
	}

	@Test
	// This test might fail if the online petstore specification is changed for some reason.
	public void testComplexMatch() throws Exception {

		/*
		 * Parameters 1, 4, 5 and 6 aren't used in SwaggerProxyKey's complexMatch, so they are set to a dummy value.
		 */

		// Ensure that /pet is supported via POST and PUT but not via GET, HEAD nor DELETE
		assertFalse(key.complexMatch("", "GET", "/v2/pet", "", 0, ""));
		assertTrue(key.complexMatch("", "POST", "/v2/pet", "", 0, ""));
		assertFalse(key.complexMatch("", "HEAD", "/v2/pet", "", 0, ""));
		assertTrue(key.complexMatch("", "PUT", "/v2/pet", "", 0, ""));
		assertFalse(key.complexMatch("", "DELETE", "/v2/pet", "", 0, ""));

		// Check that some of the simple Swagger API operations do exist
		assertTrue(key.complexMatch("", "GET", "/v2/pet/findByStatus", "", 0, ""));
		assertTrue(key.complexMatch("", "GET", "/v2/pet/findByTags", "", 0, ""));
		assertTrue(key.complexMatch("", "GET", "/v2/store/inventory", "", 0, ""));
		assertTrue(key.complexMatch("", "POST", "/v2/store/order", "", 0, ""));
		assertTrue(key.complexMatch("", "POST", "/v2/user", "", 0, ""));
		assertTrue(key.complexMatch("", "POST", "/v2/user/createWithArray", "", 0, ""));
		assertTrue(key.complexMatch("", "POST", "/v2/user/createWithList", "", 0, ""));
		assertTrue(key.complexMatch("", "GET", "/v2/user/login", "", 0, ""));
		assertTrue(key.complexMatch("", "GET", "/v2/user/logout", "", 0, ""));

		// Check some paths with Path Template Matching
		assertTrue(key.complexMatch("", "GET", "/v2/pet/123", "", 0, "")); // GET /pet/{petId}
		assertTrue(key.complexMatch("", "POST", "/v2/pet/123", "", 0, "")); // POST /pet/{petId}
		assertFalse(key.complexMatch("", "HEAD", "/v2/pet/123", "", 0, "")); // HEAD for /pet/{petId} doesn't exist
		assertFalse(key.complexMatch("", "PUT", "/v2/pet/123", "", 0, "")); // PUT for /pet/{petId} doesn't exist
		assertTrue(key.complexMatch("", "DELETE", "/v2/pet/123", "", 0, "")); // DELETE /pet/{petId}
		assertTrue(key.complexMatch("", "POST", "/v2/pet/bello/uploadImage", "", 0, ""));
	}

}
