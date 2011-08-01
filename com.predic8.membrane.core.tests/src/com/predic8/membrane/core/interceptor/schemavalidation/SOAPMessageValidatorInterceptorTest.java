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

package com.predic8.membrane.core.interceptor.schemavalidation;

import static junit.framework.Assert.assertEquals;

import java.io.InputStreamReader;

import org.junit.Before;
import org.junit.Test;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.util.MessageUtil;
import com.predic8.membrane.core.util.TextUtil;


public class SOAPMessageValidatorInterceptorTest {
	
	private Request requestTB;
	
	private Request requestXService;
	
	private Exchange exc;
	
	public static final String ARTICLE_SERVICE_WSDL = "resources/validation/ArticleService.xml";
	
	public static final String BLZ_SERVICE_WSDL = "resources/validation/BLZService.xml";
	
	public static final String E_MAIL_SERVICE_WSDL = "resources/validation/XWebEmailValidation.wsdl.xml";
	
	@Before
	public void setUp() throws Exception {
		requestTB = MessageUtil.getPostRequest("http://thomas-bayer.com");
		requestXService = MessageUtil.getPostRequest("http://ws.xwebservices.com");
		exc = new Exchange();
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
	public void testHandleRequestInvalidArticleMessage() throws Exception {
		assertEquals(Outcome.ABORT, getOutcome(requestTB, createValidatorInterceptor(ARTICLE_SERVICE_WSDL), "/articleRequestInvalid.xml"));
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
	
	private Outcome getOutcome(Request request, Interceptor interceptor, String fileName) throws Exception {
		request.setBodyContent(getContent(fileName).getBytes());
		exc.setRequest(request);
		return interceptor.handleRequest(exc);
	}
	
	private String getContent(String fileName) {
		return TextUtil.formatXML(new InputStreamReader(this.getClass().getResourceAsStream(fileName)));
	}
	
	private SoapValidatorInterceptor createValidatorInterceptor(String wsdl) throws Exception {
		SoapValidatorInterceptor interceptor = new SoapValidatorInterceptor();
		interceptor.setWsdl(wsdl);
		interceptor.init();
		return interceptor;
	}
	
}
