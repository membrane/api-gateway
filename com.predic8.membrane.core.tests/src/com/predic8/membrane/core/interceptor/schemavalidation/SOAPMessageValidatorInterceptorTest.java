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
	
	private SOAPMessageValidatorInterceptor createValidatorInterceptor(String wsdl) throws Exception {
		SOAPMessageValidatorInterceptor interceptor = new SOAPMessageValidatorInterceptor();
		interceptor.setWsdl(wsdl);
		interceptor.init();
		return interceptor;
	}
	
}
