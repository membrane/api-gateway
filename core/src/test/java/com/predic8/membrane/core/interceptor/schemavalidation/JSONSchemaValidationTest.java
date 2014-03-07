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

import java.io.IOException;

import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Test;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.interceptor.schemavalidation.ValidatorInterceptor.FailureHandler;
import com.predic8.membrane.core.resolver.ResolverMap;

public class JSONSchemaValidationTest {

	private void validate(String schema, String json, boolean success) throws IOException, Exception {
		final StringBuffer sb = new StringBuffer();
		FailureHandler fh = new FailureHandler() {
			@Override
			public void handleFailure(String message, Exchange exc) {
				sb.append(message);
				sb.append("\n");
			}
		};
		JSONValidator jsonValidator = new JSONValidator(new ResolverMap(), schema, fh);
		Request request = new Request.Builder().body(IOUtils.toByteArray(getClass().getResourceAsStream(json))).build();
		Exchange exchange = new Exchange(null);
		jsonValidator.validateMessage(exchange, request, "request");
		if (success)
			Assert.assertTrue(sb.toString(), sb.length() == 0);
		else
			Assert.assertTrue("No error occurred, but expected one.", sb.length() != 0);
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
