package com.predic8.membrane.core.interceptor.rewrite;

import static org.junit.Assert.assertEquals;

import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.util.MessageUtil;
public class RegExURLRewriteInterceptorTest {

	private Exchange exc;
	
	@Before
	public void setUp() throws Exception {
		exc = new Exchange();
		exc.setRequest(MessageUtil.getGetRequest("/buy/banana/3"));
	}

	@Test
	public void testRewrite() throws Exception {
		RegExURLRewriteInterceptor interceptor = new RegExURLRewriteInterceptor();
		Map<String, String> mapping = new HashMap<String, String>();
		mapping.put("/buy/(.*)/(.*)", "/buy?item=$1&amount=$2");
		interceptor.setMapping(mapping);
		interceptor.handleRequest(exc);
		
		assertEquals("/buy?item=banana&amount=3", exc.getRequest().getUri());
	}
	
}
