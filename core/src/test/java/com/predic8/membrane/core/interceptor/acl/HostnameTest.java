/* Copyright 2013 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.acl;

import java.net.UnknownHostException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.predic8.membrane.core.Router;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class HostnameTest {

	static Router router;

	static Hostname h1;

	static Ip ip;

	@BeforeAll
	public static void setUp() throws Exception {

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

		//		System.out.println(router.getDnsCache().getCanonicalHostName(byName));
		//		System.out.println(byName.getCanonicalHostName());
		//		System.out.println("Local Host: " + InetAddress.getLocalHost());
		//		System.out.println(h1.matches(byName));
		//		System.out.println(ip.matches(byName));
		assertEquals(b, h1.matches(address, address));
	}
}
