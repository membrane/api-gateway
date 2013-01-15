package com.predic8.membrane.core.interceptor.acl;

import java.net.InetAddress;
import java.net.UnknownHostException;

import junit.framework.TestCase;

import org.junit.Before;
import org.junit.Test;

import com.predic8.membrane.core.Router;

public class HostnameTest extends TestCase {

	Router router;
	
	Hostname h1;

	Ip ip;
	
	@Before
	public void setUp() throws Exception {
		
		router = new Router();
		
		h1 = new Hostname(router);
		h1.setPattern("localhost");
		
		ip = new Ip(router);
		ip.setPattern("127.0.0.1");
	}
	
	@Test
	public void test_irgendwas() throws UnknownHostException {
		check("213.217.99.141", false);
	}
	
	@Test
	public void test_127_0_0_1_matches_localhost_pattern() throws UnknownHostException {
		check("127.0.0.1", true);
	}
	
	@Test
	public void test_localhost_matches_localhost_pattern() throws UnknownHostException{
		check("localhost", true);
	}

	@Test
	public void test_ipv6_long_matches_localhost_pattern() throws UnknownHostException{
		check("0:0:0:0:0:0:0:1", true);
	}
	
	@Test
	public void test_ipv6_short_matches_localhost_pattern() throws UnknownHostException{
		check("::1", true);
	}
	
	@Test
	public void test_192_168_1_1_matches_localhost_pattern() throws UnknownHostException{
		check("192.168.1.1", false);
	}
	
	private void check(String address, boolean b) throws UnknownHostException {
		
		InetAddress byName = InetAddress.getByName(address);
//		System.out.println(router.getDnsCache().getCanonicalHostName(byName));
//		System.out.println(byName.getCanonicalHostName());
//		System.out.println("Local Host: " + InetAddress.getLocalHost());
//		System.out.println(h1.matches(byName));
//		System.out.println(ip.matches(byName));
		assertEquals(b, h1.matches(byName));
	}
}
