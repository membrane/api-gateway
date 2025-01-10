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
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.resolver.*;
import com.predic8.membrane.core.util.*;
import org.apache.commons.io.*;
import org.jetbrains.annotations.*;
import org.junit.jupiter.api.*;

import java.io.*;

import static com.predic8.membrane.core.interceptor.Outcome.*;
import static java.util.Objects.*;
import static org.junit.jupiter.api.Assertions.*;


public class ValidatorInterceptorTest {

	private Request requestTB;

	private Request requestXService;

	private Exchange exc;

	public static final String ARTICLE_SERVICE_WSDL = "classpath:/validation/ArticleService.wsdl";

	public static final String ARTICLE_SERVICE_BOM_WSDL = "classpath:/validation/ArticleService-bom.xml";

	public static final String BLZ_SERVICE_WSDL = "classpath:/validation/BLZService.xml";

	public static final String E_MAIL_SERVICE_WSDL = "classpath:/validation/XWebEmailValidation.wsdl.xml";

	@BeforeEach
	public void setUp() {
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
		assertEquals(ABORT, getOutcome(requestTB, createValidatorInterceptor(BLZ_SERVICE_WSDL), "/getBankInvalid.xml"));
	}

	@Test
	public void testHandleRequestValidArticleMessage() throws Exception {
		assertEquals(CONTINUE, getOutcome(requestTB, createValidatorInterceptor(ARTICLE_SERVICE_WSDL), "/validation/articleRequest.xml"));
	}

	@Test
	public void testHandleRequestValidArticleMessageBOM() throws Exception {
		assertEquals(CONTINUE, getOutcome(requestTB, createValidatorInterceptor(ARTICLE_SERVICE_BOM_WSDL), "/validation/articleRequest-bom.xml"));
	}

	@Test
	public void testHandleNonSOAPXMLMessage() throws Exception {
		assertEquals(ABORT, getOutcome(requestTB, createValidatorInterceptor(ARTICLE_SERVICE_WSDL), "/customer.xml"));
	}

	@Test
	public void testHandleRequestInvalidArticleMessage() throws Exception {
		assertEquals(ABORT, getOutcome(requestTB, createValidatorInterceptor(ARTICLE_SERVICE_WSDL), "/validation/articleRequestInvalid.xml"));
	}

	@Test
	public void testHandleRequestInvalidArticleMessageBOM() throws Exception {
		assertEquals(ABORT, getOutcome(requestTB, createValidatorInterceptor(ARTICLE_SERVICE_BOM_WSDL), "/validation/articleRequestInvalid-bom.xml"));
	}

	@Test
	public void testHandleResponseValidArticleMessage() throws Exception {
		exc.setRequest(requestTB);
		exc.setResponse(Response.ok().body(getContent("/validation/articleResponse.xml")).build());
		assertEquals(CONTINUE, createValidatorInterceptor(ARTICLE_SERVICE_WSDL).handleResponse(exc));
	}

	@Test
	public void testHandleResponseValidArticleMessageGzipped() throws Exception {
		exc.setRequest(requestTB);
		exc.setResponse(Response.ok().body(getContent("/validation/articleResponse.xml.gz")).header("Content-Encoding", "gzip").build());
		assertEquals(CONTINUE, createValidatorInterceptor(ARTICLE_SERVICE_WSDL).handleResponse(exc));
	}

	@Test
	public void testHandleRequestValidEmailMessage() throws Exception {
		assertEquals(CONTINUE, getOutcome(requestXService, createValidatorInterceptor(E_MAIL_SERVICE_WSDL), "/validation/validEmail.xml"));
	}

	@Test
	public void testHandleRequestInvalidEmailMessageDoubleEMailElement() throws Exception {
		assertEquals(ABORT, getOutcome(requestXService, createValidatorInterceptor(E_MAIL_SERVICE_WSDL), "/validation/invalidEmail.xml"));
	}

	@Test
	public void testHandleRequestInvalidEmailMessageDoubleRequestElement() throws Exception {
		assertEquals(ABORT, getOutcome(requestXService, createValidatorInterceptor(E_MAIL_SERVICE_WSDL), "/validation/invalidEmail2.xml"));
	}

	@Test
	public void testHandleRequestInvalidEmailMessageUnknownElement() throws Exception {
		assertEquals(ABORT, getOutcome(requestXService, createValidatorInterceptor(E_MAIL_SERVICE_WSDL), "/validation/invalidEmail3.xml"));
	}

	@Test
	public void testSchemaValidation() throws Exception {
		assertEquals(CONTINUE, getOutcome(requestTB, createSchemaValidatorInterceptor("src/test/resources/validation/order.xsd"), "/validation/order.xml"));
		assertEquals(ABORT, getOutcome(requestTB, createSchemaValidatorInterceptor("src/test/resources/validation/order.xsd"), "/validation/invalid-order.xml"));
	}

	private Outcome getOutcome(Request request, Interceptor interceptor, String fileName) throws Exception {
		request.setBodyContent(getContent(fileName));
		exc.setRequest(request);
		return interceptor.handleRequest(exc);
	}

	private byte[] getContent(String fileName) throws IOException {
		return IOUtils.toByteArray(requireNonNull(this.getClass().getResourceAsStream(fileName)));
	}

	private ValidatorInterceptor createSchemaValidatorInterceptor(String schema) {
		ValidatorInterceptor interceptor = createValidatorInterceptor();
		interceptor.setSchema(schema);
		interceptor.init(new Router());
		return interceptor;
	}

	private ValidatorInterceptor createValidatorInterceptor(String wsdl) {
		ValidatorInterceptor interceptor = createValidatorInterceptor();
		interceptor.setWsdl(wsdl);
		interceptor.init(new Router());
		return interceptor;
	}

	private static @NotNull ValidatorInterceptor createValidatorInterceptor() {
		ValidatorInterceptor interceptor = new ValidatorInterceptor();
		interceptor.setResourceResolver(new ResolverMap());
		return interceptor;
	}
}
