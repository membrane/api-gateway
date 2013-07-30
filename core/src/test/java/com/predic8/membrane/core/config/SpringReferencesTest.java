package com.predic8.membrane.core.config;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.List;

import org.junit.Assert;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.interceptor.LogInterceptor;
import com.predic8.membrane.core.interceptor.SpringInterceptor;
import com.predic8.membrane.core.rules.ServiceProxy;

public class SpringReferencesTest {

	private Router r;

	@Before
	public void before() throws MalformedURLException {
		r = Router.init("classpath:/proxies-using-spring-refs.xml");
	}
	
	@Test
	public void doit() {
		ServiceProxy p = (ServiceProxy) r.getRules().iterator().next();
		List<Interceptor> is = p.getInterceptors();
		
		Assert.assertEquals(LogInterceptor.class, is.get(0).getClass());
		Assert.assertEquals(LogInterceptor.class, is.get(1).getClass());
		Assert.assertEquals(LogInterceptor.class, is.get(2).getClass());
		Assert.assertEquals(SpringInterceptor.class, is.get(3).getClass());
		Assert.assertEquals(LogInterceptor.class, is.get(4).getClass());
		
		SpringInterceptor si = (SpringInterceptor) is.get(3);
		
		Assert.assertSame(is.get(1), is.get(2));
		Assert.assertSame(is.get(1), si.getInner());
	}
	
	@After
	public void after() throws IOException {
		r.shutdown();
	}
}
