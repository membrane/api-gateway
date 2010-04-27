package com.predic8.membrane.core.interceptor.acl;

import org.junit.Test;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.exchange.HttpExchange;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.util.TestUtil;

import junit.framework.TestCase;

public class AccessControlInterceptorTest extends TestCase {

	private AccessControlInterceptor interceptor;
	
	private Exchange exc;
	
	@Override
	protected void setUp() throws Exception {
		
		exc = new HttpExchange();
		exc.setRequest(TestUtil.getGetRequest());
		
		
		interceptor = new AccessControlInterceptor();
		interceptor.setAclFilename("resources/acl.xml");
	}
	
	@Test
	public void testDefaultAccess() throws Exception {
		assertEquals(Outcome.ABORT, interceptor.handleRequest(exc));
	}
	
}
