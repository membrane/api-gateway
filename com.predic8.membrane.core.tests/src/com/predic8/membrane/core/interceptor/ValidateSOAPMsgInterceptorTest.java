package com.predic8.membrane.core.interceptor;

import java.io.InputStreamReader;

import junit.framework.TestCase;

import com.predic8.membrane.core.exchange.HttpExchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.interceptor.schemavalidation.ValidateSOAPMsgInterceptor;
import com.predic8.membrane.core.util.MessageUtil;
import com.predic8.membrane.core.util.TextUtil;

public class ValidateSOAPMsgInterceptorTest extends TestCase {

	private ValidateSOAPMsgInterceptor interceptorForBlz;

	private ValidateSOAPMsgInterceptor interceptorForArticle;
	
	private Request request;
	
	private HttpExchange exc;
	
	public static final String ARTICLE_SERVICE_WSDL = "http://www.predic8.com:8080/material/ArticleService?wsdl";
	
	public static final String BLZ_SERVICE_WSDL = "http://www.thomas-bayer.com/axis2/services/BLZService?wsdl";
	
	@Override
	protected void setUp() throws Exception {
		request = MessageUtil.getPostRequest("http://thomas-bayer.com");
		exc = new HttpExchange();
		
		interceptorForBlz = createValidatorInterceptor(BLZ_SERVICE_WSDL);
		interceptorForArticle = createValidatorInterceptor(ARTICLE_SERVICE_WSDL);
	}

	public void testHandleRequestValidBLZMessage() throws Exception {
		assertEquals(Outcome.CONTINUE, getOutcome(interceptorForBlz, "/getBank.xml"));
	}

	public void testHandleRequestInvalidBLZMessage() throws Exception {
		assertEquals(Outcome.ABORT, getOutcome(interceptorForBlz, "/getBankInvalid.xml"));		
	}
	
	public void testHandleRequestValidArticleMessage() throws Exception {
		assertEquals(Outcome.CONTINUE, getOutcome(interceptorForArticle, "/articleRequest.xml"));
	}
	
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
	
	private ValidateSOAPMsgInterceptor createValidatorInterceptor(String wsdl) {
		ValidateSOAPMsgInterceptor interceptor = new ValidateSOAPMsgInterceptor();
		interceptor.setWsdl(wsdl);
		interceptor.init();
		return interceptor;
	}
	
}
