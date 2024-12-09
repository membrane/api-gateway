/* Copyright 2014 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.schemavalidation;

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.schemavalidation.ValidatorInterceptor.*;
import com.predic8.membrane.core.resolver.*;
import org.junit.jupiter.api.*;

import java.io.*;

import static org.apache.commons.io.IOUtils.*;
import static org.junit.jupiter.api.Assertions.*;

public class JSONSchemaValidationTest {

	private void validate(String schema, String json, boolean success) throws Exception {
		final StringBuffer sb = new StringBuffer();
		FailureHandler fh = (message, exc) -> {
            sb.append(message);
            sb.append("\n");
        };
		JSONValidator validator = new JSONValidator(new ResolverMap(), schema, fh);
		validator.init();
		Request request = new Request.Builder().body(toByteArray(getClass().getResourceAsStream(json))).build();
		Exchange exchange = new Exchange(null);
		validator.validateMessage(exchange, request);
		if (success)
            assertEquals(0, sb.length(), sb.toString());
		else
            assertFalse(sb.isEmpty(), "No error occurred, but expected one.");
	}

	@Test
	public void run() throws Exception {
		validate("classpath:/validation/jsonschema/schema2000.json", "/validation/jsonschema/good2000.json", true);
	}

	@Test
	public void run2() throws Exception {
		validate("classpath:/validation/jsonschema/schema2000.json", "/validation/jsonschema/bad2000.json", false);
	}

	@Test
	public void run3() throws Exception {
		validate("classpath:/validation/jsonschema/schema2001.json", "/validation/jsonschema/good2001.json", true);
	}

	@Test
	public void run4() throws Exception {
		validate("classpath:/validation/jsonschema/schema2001.json", "/validation/jsonschema/bad2001.json", false);
	}


}
