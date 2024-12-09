/* Copyright 2011, 2012 predic8 GmbH, www.predic8.com

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
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.resolver.*;
import com.predic8.membrane.core.util.*;
import org.junit.jupiter.api.*;

import java.io.*;

import static com.predic8.membrane.core.interceptor.Outcome.*;
import static java.util.Objects.*;
import static org.junit.jupiter.api.Assertions.*;


public class SOAPMessageValidatorInterceptorTest {

	private static Request requestTB;

	private static Request requestXService;

	private static Exchange exc;

	public static final String ARTICLE_SERVICE_WSDL = "src/test/resources/validation/ArticleService.wsdl";

	public static final String BLZ_SERVICE_WSDL = "src/test/resources/validation/BLZService.xml";

	public static final String E_MAIL_SERVICE_WSDL = "src/test/resources/validation/XWebEmailValidation.wsdl.xml";

	public static final String INLINE_ANYTYPE_WSDL = "src/test/resources/validation/inline-anytype.wsdl";

	@BeforeAll
	public static void setUp() throws Exception {
		requestTB = MessageUtil.getPostRequest("http://thomas-bayer.com");
		requestXService = MessageUtil.getPostRequest("http://ws.xwebservices.com");
		exc = new Exchange(null);
	}

	@Test
	public void testHandleRequestValidBLZMessage() throws Exception {
		assertEquals(CONTINUE, getOutcome(requestTB, createValidatorInterceptor(BLZ_SERVICE_WSDL), "/getBank.xml"));
	}

	@Test
	public void testHandleRequestInvalidBLZMessage() throws Exception {
		assertEquals(Outcome.ABORT, getOutcome(requestTB, createValidatorInterceptor(BLZ_SERVICE_WSDL), "/getBankInvalid.xml"));
	}

	@Test
	public void testHandleRequestValidArticleMessage() throws Exception {
		assertEquals(CONTINUE, getOutcome(requestTB, createValidatorInterceptor(ARTICLE_SERVICE_WSDL), "/validation/articleRequest.xml"));
	}

	@Test
	public void testHandleRequestInvalidArticleMessage() throws Exception {
		assertEquals(Outcome.ABORT, getOutcome(requestTB, createValidatorInterceptor(ARTICLE_SERVICE_WSDL), "/validation/articleRequestInvalid.xml"));
	}

	@Test
	public void testHandleRequestValidEmailMessage() throws Exception {
		assertEquals(CONTINUE, getOutcome(requestXService, createValidatorInterceptor(E_MAIL_SERVICE_WSDL), "/validation/validEmail.xml"));
	}

	@Test
	public void testHandleRequestInvalidEmailMessageDoubleEMailElement() throws Exception {
		assertEquals(Outcome.ABORT, getOutcome(requestXService, createValidatorInterceptor(E_MAIL_SERVICE_WSDL), "/validation/invalidEmail.xml"));
	}

	@Test
	public void testHandleRequestInvalidEmailMessageDoubleRequestElement() throws Exception {
		assertEquals(Outcome.ABORT, getOutcome(requestXService, createValidatorInterceptor(E_MAIL_SERVICE_WSDL), "/validation/invalidEmail2.xml"));
	}

	@Test
	public void testHandleRequestInvalidEmailMessageUnknownElement() throws Exception {
		assertEquals(Outcome.ABORT, getOutcome(requestXService, createValidatorInterceptor(E_MAIL_SERVICE_WSDL), "/validation/invalidEmail3.xml"));
	}

	@Disabled(value="This is a problem in the soa-model dependency.")
	@Test
	public void testInlineSchemaWithAnyType() throws Exception {
		assertEquals(Outcome.ABORT, getOutcome(requestXService, createValidatorInterceptor(INLINE_ANYTYPE_WSDL), "/validation/invalidEmail3.xml"));
	}

	private Outcome getOutcome(Request request, Interceptor interceptor, String fileName) throws Exception {
		request.setBodyContent(getContent(fileName).getBytes());
		exc.setRequest(request);
		return interceptor.handleRequest(exc);
	}

	private String getContent(String fileName) {
		return TextUtil.formatXML(new InputStreamReader(requireNonNull(this.getClass().getResourceAsStream(fileName))));
	}

	private ValidatorInterceptor createValidatorInterceptor(String wsdl) throws Exception {
		ValidatorInterceptor interceptor = new ValidatorInterceptor();
		interceptor.setWsdl(wsdl);
		interceptor.setResourceResolver(new ResolverMap());
		interceptor.init();
		return interceptor;
	}
}
