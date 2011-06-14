package com.predic8.membrane.core.util;


import java.net.InetAddress;
import java.net.UnknownHostException;

import junit.framework.TestCase;

import org.junit.Test;

public class DNSCacheTest extends TestCase {

	private DNSCache cache = new DNSCache();
	
	private static InetAddress address;
	
	static {
		try {
			address = InetAddress.getByName("localhost");
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
	}
	
	@Test
	public void testGetHostName() throws Exception {
		String host = cache.getHostName(address);
		assertEquals("localhost", host);
		assertTrue(cache.getCachedHostNames().contains(host));
	}
	
	@Test
	public void testGetHostAddress() throws Exception {
		String host = cache.getHostAddress(address);
		assertEquals("127.0.0.1", host);
		assertTrue(cache.getCachedHostAddresses().contains(host));
	}
	
}
