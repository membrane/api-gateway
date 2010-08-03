package com.predic8.membrane.core.interceptor.rewrite;

import java.util.HashMap;
import java.util.Map;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.exchange.HttpExchange;
import com.predic8.membrane.core.util.TestUtil;

import junit.framework.TestCase;

public class SimpleURLRewriteInterceptorTest extends TestCase {

	private SimpleURLRewriteInterceptor interceptor;
	
	private Exchange exc;
	
	protected void setUp() throws Exception {
		
		exc = new HttpExchange();
		exc.setRequest(TestUtil.getGetRequest("/service?wsdl"));
	}

	public void testRewrite() throws Exception {
		interceptor = new SimpleURLRewriteInterceptor();
		Map<String, String> mapping = new HashMap<String, String>();
		mapping.put("/service?wsdl", "/services/subscribe.txt");
		interceptor.setMapping(mapping );
		interceptor.handleRequest(exc);
		
		
		assertEquals("/services/subscribe.txt", exc.getRequest().getUri());
	}
	
}
