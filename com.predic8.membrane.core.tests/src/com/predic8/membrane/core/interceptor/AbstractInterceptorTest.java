package com.predic8.membrane.core.interceptor;

import static junit.framework.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.predic8.membrane.core.interceptor.acl.AccessControlInterceptor;
import com.predic8.membrane.core.interceptor.rewrite.SimpleURLRewriteInterceptor;

public class AbstractInterceptorTest {

	
	private Interceptor i100;
	
	private Interceptor i200;
	
	private Interceptor i300;
	
	private Interceptor i400;
	
	@Before
	public void setUp() throws Exception {
		i100 = new SimpleURLRewriteInterceptor();
		i100.setPriority(100);
		
		i200 = new DispatchingInterceptor();
		i200.setPriority(200);
		
		i300 = new AccessControlInterceptor();
		i300.setPriority(300);
		
		i400 = new ExchangeStoreInterceptor();
		i400.setPriority(400);
	}

	@Test
	public void testSorting() throws Exception {
		List<Interceptor> interceptors = new ArrayList<Interceptor>();
		interceptors.add(i400);
		interceptors.add(i100);
		interceptors.add(i300);
		interceptors.add(i200);
		
		Collections.sort(interceptors);
		
		assertEquals(0, interceptors.indexOf(i100));
		assertEquals(1, interceptors.indexOf(i200));
		assertEquals(2, interceptors.indexOf(i300));
		assertEquals(3, interceptors.indexOf(i400));
	}
	
}

