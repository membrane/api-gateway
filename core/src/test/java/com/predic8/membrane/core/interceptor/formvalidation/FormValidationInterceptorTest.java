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

import static org.junit.Assert.assertEquals;

import java.util.*;

import org.junit.*;

import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.formvalidation.FormValidationInterceptor.Field;
import com.predic8.membrane.core.util.MessageUtil;
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

		List<Field> fields = new ArrayList<Field>();
		fields.add(amount);		
		fields.add(article);		
		interceptor.setFields(fields);
		
		Exchange exc = getExchange("/buy?article=pizza&amount=five");
		assertEquals(Outcome.ABORT, interceptor.handleRequest(exc));		
		assertEquals(400, exc.getResponse().getStatusCode());

		exc = getExchange("/buy?article=pizza&amount=2");
		assertEquals(Outcome.ABORT, interceptor.handleRequest(exc));		
		assertEquals(400, exc.getResponse().getStatusCode());

		exc = getExchange("/buy?article=banana&amount=five");
		assertEquals(Outcome.ABORT, interceptor.handleRequest(exc));		
		assertEquals(400, exc.getResponse().getStatusCode());

		exc = getExchange("/buy?article=banana&amount=5");
		assertEquals(Outcome.CONTINUE, interceptor.handleRequest(exc));		
	}
	
	private Exchange getExchange(String uri) {
		Exchange exc = new Exchange(null);
		exc.setRequest(MessageUtil.getGetRequest(uri));
		return exc;
	}
	
}
