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

import static org.junit.Assert.assertEquals;

import java.io.IOException;

import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.resolver.ResolverMap;
import com.predic8.membrane.core.util.MessageUtil;


public class ValidatorInterceptorTest {
	
	private Request requestTB;
	
	private Request requestXService;
	
	private Exchange exc;
	
	public static final String ARTICLE_SERVICE_WSDL = "classpath:/validation/ArticleService.xml";
	
	public static final String BLZ_SERVICE_WSDL = "classpath:/validation/BLZService.xml";
	
	public static final String E_MAIL_SERVICE_WSDL = "classpath:/validation/XWebEmailValidation.wsdl.xml";
	
	@Before
	public void setUp() throws Exception {
		requestTB = MessageUtil.getPostRequest("http://thomas-bayer.com");
		requestXService = MessageUtil.getPostRequest("http://ws.xwebservices.com");
		exc = new Exchange(null);
	}
	
	@Test
	public void testHandleRequestValidBLZMessage() throws Exception {
		assertEquals(Outcome.CONTINUE, getOutcome(requestTB, createValidatorInterceptor(BLZ_SERVICE_WSDL), "/getBank.xml"));
	}
	
	@Test
	public void testHandleRequestInvalidBLZMessage() throws Exception {
		assertEquals(Outcome.ABORT, getOutcome(requestTB, createValidatorInterceptor(BLZ_SERVICE_WSDL), "/getBankInvalid.xml"));		
	}
	
	@Test
	public void testHandleRequestValidArticleMessage() throws Exception {
		assertEquals(Outcome.CONTINUE, getOutcome(requestTB, createValidatorInterceptor(ARTICLE_SERVICE_WSDL), "/articleRequest.xml"));
	}

	@Test
	public void testHandleNonSOAPXMLMessage() throws Exception {
		assertEquals(Outcome.ABORT, getOutcome(requestTB, createValidatorInterceptor(ARTICLE_SERVICE_WSDL), "/customer.xml"));
	}

	@Test
	public void testHandleRequestInvalidArticleMessage() throws Exception {
		assertEquals(Outcome.ABORT, getOutcome(requestTB, createValidatorInterceptor(ARTICLE_SERVICE_WSDL), "/articleRequestInvalid.xml"));
	}

	@Test
	public void testHandleResponseValidArticleMessage() throws Exception {
		exc.setRequest(requestTB);
		exc.setResponse(Response.ok().body(getContent("/articleResponse.xml")).build());
		assertEquals(Outcome.CONTINUE, createValidatorInterceptor(ARTICLE_SERVICE_WSDL).handleResponse(exc));
	}

	@Test
	public void testHandleResponseValidArticleMessageGzipped() throws Exception {
		exc.setRequest(requestTB);
		exc.setResponse(Response.ok().body(getContent("/articleResponse.xml.gz")).header("Content-Encoding", "gzip").build());
		assertEquals(Outcome.CONTINUE, createValidatorInterceptor(ARTICLE_SERVICE_WSDL).handleResponse(exc));
	}

	@Test
	public void testHandleRequestValidEmailMessage() throws Exception {
		assertEquals(Outcome.CONTINUE, getOutcome(requestXService, createValidatorInterceptor(E_MAIL_SERVICE_WSDL), "/validation/validEmail.xml"));
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
	
	@Test
	public void testSchemaValidation() throws Exception {
		assertEquals(Outcome.CONTINUE, getOutcome(requestTB, createSchemaValidatorInterceptor("src/test/resources/validation/order.xsd"), "/validation/order.xml"));
		assertEquals(Outcome.ABORT, getOutcome(requestTB, createSchemaValidatorInterceptor("src/test/resources/validation/order.xsd"), "/validation/invalid-order.xml"));
	}

	private Outcome getOutcome(Request request, Interceptor interceptor, String fileName) throws Exception {
		request.setBodyContent(getContent(fileName));
		exc.setRequest(request);
		return interceptor.handleRequest(exc);
	}
	
	private byte[] getContent(String fileName) throws IOException {
		return IOUtils.toByteArray(this.getClass().getResourceAsStream(fileName));
	}
	
	private ValidatorInterceptor createSchemaValidatorInterceptor(String schema) throws Exception {
		ValidatorInterceptor interceptor = new ValidatorInterceptor();
		interceptor.setResourceResolver(new ResolverMap());
		interceptor.setSchema(schema);
		interceptor.init();
		return interceptor;
	}

	private ValidatorInterceptor createValidatorInterceptor(String wsdl) throws Exception {
		ValidatorInterceptor interceptor = new ValidatorInterceptor();
		interceptor.setResourceResolver(new ResolverMap());
		interceptor.setWsdl(wsdl);
		interceptor.init();
		return interceptor;
	}
	
}
