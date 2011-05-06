package com.predic8.membrane.core.config;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;

import com.predic8.membrane.core.MockRouter;


public class ProxyTest {

	private Proxy proxy;
	
	@Before
	public void setUp() throws Exception {
		proxy = new Proxy(new MockRouter());
		proxy.setProxyUsername("predic8");
		proxy.setProxyPassword("secret");
	}
	
	@Test
	public void testGetCredentials() throws Exception {
		String credentials = proxy.getCredentials();
		assertEquals("Basic cHJlZGljODpzZWNyZXQ=", credentials);
	}
	
}
