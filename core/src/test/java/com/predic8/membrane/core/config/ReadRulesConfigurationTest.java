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
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.rules.ServiceProxy;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ReadRulesConfigurationTest {

	private static Router router;

	private static List<Rule> rules;

	@BeforeAll
	public static void setUp() throws Exception {
        router = Router.init("classpath:/proxies.xml");
		rules = router.getRuleManager().getRules();
	}

	@Test
	void testRulesSize() {
		assertEquals(3, rules.size());
	}

	@Test
	void testProxyRuleListenPort() {
		assertEquals(3090, rules.get(0).getKey().getPort());
	}

	@Test
	void testProxyRuleBlockRequest() {
		assertTrue(rules.get(0).isBlockRequest());
	}

	@Test
	void testProxyRuleBlockResponse() {
		assertFalse(rules.get(0).isBlockResponse());
	}

	@Test
	void testServiceProxyListenPort() {
		assertEquals(3000, rules.get(1).getKey().getPort());
	}

	@Test
	void testServiceProxyTargetPort() {
		assertEquals(80, ((ServiceProxy)rules.get(1)).getTargetPort());
	}

	@Test
	void testServiceProxyTargetHost() {
		assertEquals("thomas-bayer.com", ((ServiceProxy)rules.get(1)).getTargetHost());
	}

	@Test
	void testServiceProxyDefaultMethod() {
		assertEquals("*", rules.get(1).getKey().getMethod());
	}

	@Test
	void testTestServiceProxyDefaultHost() {
		assertEquals("*", rules.get(1).getKey().getHost());
	}

	@Test
	void testServiceProxyBlockRequest() {
		assertFalse(rules.get(1).isBlockRequest());
	}

	@Test
	void testServiceProxyBlockResponse() {
		assertTrue(rules.get(1).isBlockResponse());
	}

	@Test
	void testLocalServiceProxyListenPort() {
		assertEquals(2000, rules.get(2).getKey().getPort());
	}

	@Test
	void testLocalServiceProxyTargetPort() {
		assertEquals(3011, ((ServiceProxy)rules.get(2)).getTargetPort());
	}

	@Test
	void testServiceProxyMethodSet() {
		assertEquals("GET", rules.get(2).getKey().getMethod());
	}

	@Test
	void testServiceProxyHostSet() {
		assertEquals("localhost", rules.get(2).getKey().getHost());
	}

	@Test
	void testLocalServiceProxyInboundSSL() {
		assertNull(rules.get(2).getSslInboundContext());
	}

	@Test
	void testLocalServiceProxyOutboundSSL() {
		assertNull(rules.get(2).getSslOutboundContext());
	}

	@AfterAll
	public static void tearDown() throws Exception {
		router.shutdown();
	}

}
