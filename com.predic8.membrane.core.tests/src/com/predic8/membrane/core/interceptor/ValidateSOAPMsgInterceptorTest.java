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
package com.predic8.membrane.core.interceptor;

import static junit.framework.Assert.assertEquals;

import java.io.InputStreamReader;

import org.junit.Before;
import org.junit.Test;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.interceptor.schemavalidation.ValidateSOAPMsgInterceptor;
import com.predic8.membrane.core.util.MessageUtil;
import com.predic8.membrane.core.util.TextUtil;

public class ValidateSOAPMsgInterceptorTest {

	private ValidateSOAPMsgInterceptor interceptorForBlz;

	private ValidateSOAPMsgInterceptor interceptorForArticle;
	
	private Request request;
	
	private Exchange exc;
	
	public static final String ARTICLE_SERVICE_WSDL = "http://www.predic8.com:8080/material/ArticleService?wsdl";
	
	public static final String BLZ_SERVICE_WSDL = "http://www.thomas-bayer.com/axis2/services/BLZService?wsdl";
	
	@Before
	public void setUp() throws Exception {
		request = MessageUtil.getPostRequest("http://thomas-bayer.com");
		exc = new Exchange();
		
		interceptorForBlz = createValidatorInterceptor(BLZ_SERVICE_WSDL);
		interceptorForArticle = createValidatorInterceptor(ARTICLE_SERVICE_WSDL);
	}

	@Test
	public void testHandleRequestValidBLZMessage() throws Exception {
		assertEquals(Outcome.CONTINUE, getOutcome(interceptorForBlz, "/getBank.xml"));
	}

	@Test
	public void testHandleRequestInvalidBLZMessage() throws Exception {
		assertEquals(Outcome.ABORT, getOutcome(interceptorForBlz, "/getBankInvalid.xml"));		
	}
	
	@Test
	public void testHandleRequestValidArticleMessage() throws Exception {
		assertEquals(Outcome.CONTINUE, getOutcome(interceptorForArticle, "/articleRequest.xml"));
	}
	
	@Test
	public void testHandleRequestInvalidArticleMessage() throws Exception {
		assertEquals(Outcome.ABORT, getOutcome(interceptorForArticle, "/articleRequestInvalid.xml"));
	}
	
	private Outcome getOutcome(Interceptor interceptor, String fileName) throws Exception {
		request.setBodyContent(getContent(fileName).getBytes());
		exc.setRequest(request);
		return interceptor.handleRequest(exc);
	}
	
	private String getContent(String fileName) {
		return TextUtil.formatXML(new InputStreamReader(this.getClass().getResourceAsStream(fileName)));
	}
	
	private ValidateSOAPMsgInterceptor createValidatorInterceptor(String wsdl) throws Exception {
		ValidateSOAPMsgInterceptor interceptor = new ValidateSOAPMsgInterceptor();
		interceptor.setWsdl(wsdl);
		interceptor.init();
		return interceptor;
	}
	
}
