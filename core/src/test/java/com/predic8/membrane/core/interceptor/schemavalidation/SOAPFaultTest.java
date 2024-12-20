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
package com.predic8.membrane.core.interceptor.schemavalidation;

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.util.*;
import org.junit.jupiter.api.*;

import java.io.*;

import static com.predic8.membrane.core.util.SOAPUtil.FaultCode.Server;
import static com.predic8.membrane.test.AssertUtils.*;
import static org.junit.jupiter.api.Assertions.*;

public class SOAPFaultTest {
	Router r = new Router();

	@Test
	public void testValidateFaults() throws Exception {
		ValidatorInterceptor i = createValidatorInterceptor(false);
		Exchange exc = createFaultExchange();
		assertEquals(Outcome.ABORT, i.handleResponse(exc));
		assertContainsNot("secret", exc.getResponse().toString());
	}

	@Test
	public void testSkipFaults() throws Exception {
		ValidatorInterceptor i = createValidatorInterceptor(true);
		Exchange exc = createFaultExchange();
		assertEquals(Outcome.CONTINUE, i.handleResponse(exc));
		assertContains("secret", exc.getResponse().toString());
	}

	@Test
	public void testSkipFault2() throws Exception {
		ValidatorInterceptor i = createValidatorInterceptor(true);
		Exchange exc = getExchangeCP("wsdlValidator/soapFaultCustom.xml");

		assertEquals(Outcome.CONTINUE, i.handleResponse(exc));
	}

	private Exchange getExchangeCP(String path) throws IOException {
		Exchange exc = new Exchange(null);
		exc.setResponse(Response.ok().contentType("text/xml").body(getClass().getClassLoader().getResourceAsStream(path), true).build());
		return exc;
	}

	private ValidatorInterceptor createValidatorInterceptor(boolean skipFaults) throws Exception {
		ValidatorInterceptor i = new ValidatorInterceptor();
		i.setWsdl("src/test/resources/validation/ArticleService.xml");
		i.setSkipFaults(skipFaults);
		i.init(r);
		return i;
	}

	private Exchange createFaultExchange() {
		Exchange exc = new Exchange(null);
		exc.setResponse(SOAPUtil.createSOAPValidationErrorResponse(Server,"secret","detail"));
		return exc;
	}

}
