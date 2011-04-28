package com.predic8.membrane.core.interceptor.rewrite;

import static junit.framework.Assert.assertEquals;

import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.exchange.HttpExchange;
import com.predic8.membrane.core.util.MessageUtil;
public class SimpleURLRewriteInterceptorTest {

	private SimpleURLRewriteInterceptor interceptor;
	
	private Exchange exc;
	
	@Before
	public void setUp() throws Exception {
		exc = new HttpExchange();
		exc.setRequest(MessageUtil.getGetRequest("/service?wsdl"));
	}

	@Test
	public void testRewrite() throws Exception {
		interceptor = new SimpleURLRewriteInterceptor();
		Map<String, String> mapping = new HashMap<String, String>();
		mapping.put("/service?wsdl", "/services/subscribe.txt");
		interceptor.setMapping(mapping );
		interceptor.handleRequest(exc);
		
		
		assertEquals("/services/subscribe.txt", exc.getRequest().getUri());
	}
	
}
