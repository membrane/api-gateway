/* Copyright 2011 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.formvalidation;

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.interceptor.formvalidation.FormValidationInterceptor.*;
import org.junit.jupiter.api.*;

import java.net.*;
import java.util.*;

import static com.predic8.membrane.core.http.Request.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static org.junit.jupiter.api.Assertions.*;

public class FormValidationInterceptorTest {

	@Test
	public void testValidation() throws Exception {
		FormValidationInterceptor interceptor = new FormValidationInterceptor();
		interceptor.init(new HttpRouter());

		Field article = new Field();
		article.setName("article");
		article.setRegex("banana|apple");

		Field amount = new Field();
		amount.setName("amount");
		amount.setRegex("\\d+");

		List<Field> fields = new ArrayList<>();
		fields.add(amount);
		fields.add(article);
		interceptor.setFields(fields);

		Exchange exc = getExchange("/buy?article=pizza&amount=five");
		assertEquals(ABORT, interceptor.handleRequest(exc));
		assertEquals(400, exc.getResponse().getStatusCode());

		exc = getExchange("/buy?article=pizza&amount=2");
		assertEquals(ABORT, interceptor.handleRequest(exc));
		assertEquals(400, exc.getResponse().getStatusCode());

		exc = getExchange("/buy?article=banana&amount=five");
		assertEquals(ABORT, interceptor.handleRequest(exc));
		assertEquals(400, exc.getResponse().getStatusCode());

		exc = getExchange("/buy?article=banana&amount=5");
		assertEquals(CONTINUE, interceptor.handleRequest(exc));
	}

	private Exchange getExchange(String uri) throws URISyntaxException {
		return get(uri).buildExchange();
	}
}
