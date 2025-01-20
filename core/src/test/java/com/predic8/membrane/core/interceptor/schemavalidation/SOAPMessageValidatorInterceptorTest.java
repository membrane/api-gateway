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

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.resolver.*;
import com.predic8.membrane.core.util.*;
import org.junit.jupiter.api.*;

import java.io.*;

import static com.predic8.membrane.core.http.Request.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static java.util.Objects.*;
import static org.junit.jupiter.api.Assertions.*;


public class SOAPMessageValidatorInterceptorTest {

	public static final String ARTICLE_SERVICE_WSDL = "src/test/resources/validation/ArticleService.wsdl";
	public static final String BLZ_SERVICE_WSDL = "src/test/resources/validation/BLZService.xml";
	public static final String E_MAIL_SERVICE_WSDL = "src/test/resources/validation/XWebEmailValidation.wsdl.xml";
	public static final String INLINE_ANYTYPE_WSDL = "src/test/resources/validation/inline-anytype.wsdl";
	public static final String WSDL_MESSAGE_VALIDATION_FAILED = "WSDL message validation failed";
	public static Router router;

	@BeforeAll
	static void setup() {
		router = new Router();
	}

	@Test
	public void testHandleRequestValidBLZMessage() throws Exception {
		Exchange exc = post("http://thomas-bayer.com")
				.body(getContent("/getBank.xml"))
				.buildExchange();
		assertEquals(CONTINUE, createValidatorInterceptor(BLZ_SERVICE_WSDL).handleRequest(exc));
	}

	@Test
	public void testHandleRequestInvalidBLZMessage() throws Exception {
		Exchange exc = post("http://thomas-bayer.com")
				.body(getContent("/getBankInvalid.xml"))
				.buildExchange();
		assertEquals(ABORT, createValidatorInterceptor(BLZ_SERVICE_WSDL).handleRequest(exc));
		assertEquals(200, exc.getResponse().getStatusCode());
		String body = exc.getResponse().getBodyAsStringDecoded();
				System.out.println("body = " + body);
		assertTrue(body.contains(WSDL_MESSAGE_VALIDATION_FAILED));
		assertTrue(body.contains("No child element is expected"));
		assertTrue(body.contains("line"));
		assertTrue(body.contains("column"));
	}

	@Test
	public void testHandleRequestValidArticleMessage() throws Exception {
		Exchange exc = post("http://thomas-bayer.com/article")
				.body(getContent("/validation/articleRequest.xml"))
				.buildExchange();
		assertEquals(CONTINUE, createValidatorInterceptor(ARTICLE_SERVICE_WSDL).handleRequest(exc));
	}

	@Test
	public void testHandleRequestInvalidArticleMessage() throws Exception {
		Exchange exc = post("http://thomas-bayer.com")
				.body(getContent("/validation/articleRequestInvalid.xml"))
				.buildExchange();
		assertEquals(ABORT, createValidatorInterceptor(ARTICLE_SERVICE_WSDL).handleRequest(exc));
		assertEquals(200, exc.getResponse().getStatusCode());

		String body = exc.getResponse().getBodyAsStringDecoded();
		assertTrue(body.contains(WSDL_MESSAGE_VALIDATION_FAILED));
		assertTrue(body.contains("must have no character"));
		assertTrue(body.contains("line"));
		assertTrue(body.contains("column"));
	}

	@Test
	public void testHandleRequestValidEmailMessage() throws Exception {
		Exchange exc = post("http://ws.xwebservices.com")
				.body(getContent("/validation/validEmail.xml"))
				.buildExchange();
		assertEquals(CONTINUE, createValidatorInterceptor(E_MAIL_SERVICE_WSDL).handleRequest(exc));
	}

	@Test
	public void testHandleRequestInvalidEmailMessageDoubleEMailElement() throws Exception {
		Exchange exc = post("http://ws.xwebservices.com")
				.body(getContent("/validation/invalidEmail.xml"))
				.buildExchange();
		assertEquals(ABORT, createValidatorInterceptor(E_MAIL_SERVICE_WSDL).handleRequest(exc));

		String body = exc.getResponse().getBodyAsStringDecoded();
		System.out.println("body = " + body);
		assertTrue(body.contains(WSDL_MESSAGE_VALIDATION_FAILED));
		assertTrue(body.contains("Invalid content"));
		assertTrue(body.contains("Email"));
		assertTrue(body.contains("line"));
		assertTrue(body.contains("column"));
	}

	@Test
	public void testHandleRequestInvalidEmailMessageDoubleRequestElement() throws Exception {
		Exchange exc = post("http://ws.xwebservices.com")
				.body(getContent("/validation/invalidEmail2.xml"))
				.buildExchange();
		assertEquals(ABORT, createValidatorInterceptor(E_MAIL_SERVICE_WSDL).handleRequest(exc));

		String body = exc.getResponse().getBodyAsStringDecoded();
//		System.out.println("body = " + body);
		assertTrue(body.contains(WSDL_MESSAGE_VALIDATION_FAILED));
		assertTrue(body.contains("Invalid content"));
		assertTrue(body.contains("ValidateEmailRequest"));
		assertTrue(body.contains("line"));
		assertTrue(body.contains("column"));
	}

	@Test
	public void testHandleRequestInvalidEmailMessageUnknownElement() throws Exception {
		Exchange exc = post("http://ws.xwebservices.com")
				.body(getContent("/validation/invalidEmail3.xml"))
				.buildExchange();
		assertEquals(ABORT, createValidatorInterceptor(E_MAIL_SERVICE_WSDL).handleRequest(exc));

		String body = exc.getResponse().getBodyAsStringDecoded();
//		System.out.println("body = " + body);
		assertTrue(body.contains(WSDL_MESSAGE_VALIDATION_FAILED));
		assertTrue(body.contains("Invalid content"));
		assertTrue(body.contains("line"));
		assertTrue(body.contains("column"));
	}

	@Test
	public void testInlineSchemaWithAnyType() throws Exception {
		Exchange exc = post("http://ws.xwebservices.com")
				.body(getContent("/validation/invalidEmail3.xml"))
				.buildExchange();
		assertEquals(ABORT, createValidatorInterceptor(INLINE_ANYTYPE_WSDL).handleRequest(exc));

		String body = exc.getResponse().getBodyAsStringDecoded();
//		System.out.println("body = " + body);
		assertTrue(body.contains(WSDL_MESSAGE_VALIDATION_FAILED));
		assertTrue(body.contains("Cannot find the declaration"));
		assertTrue(body.contains("line"));
		assertTrue(body.contains("column"));
	}

	private String getContent(String fileName) throws Exception {
		return TextUtil.formatXML(new InputStreamReader(requireNonNull(this.getClass().getResourceAsStream(fileName))));
	}

	private ValidatorInterceptor createValidatorInterceptor(String wsdl) {
		ValidatorInterceptor interceptor = new ValidatorInterceptor();
		interceptor.setWsdl(wsdl);
		interceptor.setResourceResolver(new ResolverMap());
		interceptor.init(router);
		return interceptor;
	}
}