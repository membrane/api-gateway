/* Copyright 2009, 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.config;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.rules.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ReadRulesConfigurationTest {

	private static Router router;

	private static List<Proxy> proxies;

	@BeforeAll
	public static void setUp() {
        router = Router.init("classpath:/proxies.xml");
		proxies = router.getRuleManager().getRules();
	}

	@Test
	void testRulesSize() {
		assertEquals(3, proxies.size());
	}

	@Test
	void testProxyRuleListenPort() {
		assertEquals(3090, proxies.getFirst().getKey().getPort());
	}

	@Test
	void testProxyRuleBlockRequest() {
		assertTrue(proxies.getFirst().isBlockRequest());
	}

	@Test
	void testProxyRuleBlockResponse() {
		assertFalse(proxies.getFirst().isBlockResponse());
	}

	@Test
	void testServiceProxyListenPort() {
		assertEquals(3000, proxies.get(1).getKey().getPort());
	}

	@Test
	void testServiceProxyTargetPort() {
		assertEquals(80, ((ServiceProxy) proxies.get(1)).getTargetPort());
	}

	@Test
	void testServiceProxyTargetHost() {
		assertEquals("thomas-bayer.com", ((ServiceProxy) proxies.get(1)).getTargetHost());
	}

	@Test
	void testServiceProxyDefaultMethod() {
		assertEquals("*", proxies.get(1).getKey().getMethod());
	}

	@Test
	void testTestServiceProxyDefaultHost() {
		assertEquals("*", proxies.get(1).getKey().getHost());
	}

	@Test
	void testServiceProxyBlockRequest() {
		assertFalse(proxies.get(1).isBlockRequest());
	}

	@Test
	void testServiceProxyBlockResponse() {
		assertTrue(proxies.get(1).isBlockResponse());
	}

	@Test
	void testLocalServiceProxyListenPort() {
		assertEquals(2000, proxies.get(2).getKey().getPort());
	}

	@Test
	void testLocalServiceProxyTargetPort() {
		assertEquals(3011, ((ServiceProxy) proxies.get(2)).getTargetPort());
	}

	@Test
	void testServiceProxyMethodSet() {
		assertEquals("GET", proxies.get(2).getKey().getMethod());
	}

	@Test
	void testServiceProxyHostSet() {
		assertEquals("localhost", proxies.get(2).getKey().getHost());
	}

	@Test
	void testLocalServiceProxyInboundSSL() {
        if (proxies.get(2) instanceof SSLableProxy sp) {
			assertFalse(sp.isInboundSSL());
		}
		assertTrue(true);
	}

	@Test
	void testLocalServiceProxyOutboundSSL() {
		if (proxies.get(2) instanceof SSLableProxy sp) {
			assertNull(sp.getSslOutboundContext());
		}
		assertTrue(true);
	}

	@AfterAll
	public static void tearDown() {
		router.shutdown();
	}

}
