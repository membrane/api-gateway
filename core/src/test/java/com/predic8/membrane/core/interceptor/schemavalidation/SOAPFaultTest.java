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

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.util.SOAPUtil;
import com.predic8.membrane.test.*;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.predic8.membrane.core.http.MimeType.TEXT_XML;
import static com.predic8.membrane.core.http.Response.ok;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.util.SOAPUtil.FaultCode.Server;
import static com.predic8.membrane.test.StringAssertions.assertContains;
import static com.predic8.membrane.test.StringAssertions.assertContainsNot;
import static com.predic8.membrane.test.TestUtil.getPathFromResource;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class SOAPFaultTest {
	public static final String ARTICLE_SERVICE_WSDL = getPathFromResource( "/validation/ArticleService.wsdl");
	final Router r = new Router();

	@Test
	public void testValidateFaults() {
		ValidatorInterceptor i = createValidatorInterceptor(false);
		Exchange exc = createFaultExchange();
		assertEquals(ABORT, i.handleResponse(exc));
		assertContainsNot("secret", exc.getResponse().toString());
	}

	@Test
	public void testSkipFaults() {
		ValidatorInterceptor i = createValidatorInterceptor(true);
		Exchange exc = createFaultExchange();
		assertEquals(CONTINUE, i.handleResponse(exc));
		assertContains("secret", exc.getResponse().toString());
	}

	@Test
	public void testSkipFault2() {
		ValidatorInterceptor i = createValidatorInterceptor(true);
		Exchange exc = getExchangeCP("wsdlValidator/soapFaultCustom.xml");

		assertEquals(CONTINUE, i.handleResponse(exc));
	}

	private Exchange getExchangeCP(String path) {
		Exchange exc = new Exchange(null);
		exc.setResponse(ok().contentType(TEXT_XML).body(getClass().getClassLoader().getResourceAsStream(path), true).build());
		return exc;
	}

	private ValidatorInterceptor createValidatorInterceptor(boolean skipFaults) {
		ValidatorInterceptor i = new ValidatorInterceptor();
		i.setWsdl(ARTICLE_SERVICE_WSDL);
		i.setSkipFaults(skipFaults);
		i.init(r);
		return i;
	}

	private Exchange createFaultExchange() {
		Exchange exc = new Exchange(null);
		exc.setResponse(SOAPUtil.createSOAPFaultResponse(Server,"secret", Map.of("detail","error")));
		return exc;
	}

}
