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
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class ReadRulesConfigurationTest {

	private Router router;

	private List<Rule> rules;

	@Before
	public void setUp() throws Exception {
        router = Router.init("classpath:/proxies.xml");
		rules = router.getRuleManager().getRules();
	}

	@Test
	public void testRulesSize() {
		Assert.assertEquals(3, rules.size());
	}

	@Test
	public void testProxyRuleListenPort() {
		Assert.assertEquals(3090, rules.get(0).getKey().getPort());
	}

	@Test
	public void testProxyRuleBlockRequest() {
		Assert.assertTrue(rules.get(0).isBlockRequest());
	}

	@Test
	public void testProxyRuleBlockResponse() {
		Assert.assertFalse(rules.get(0).isBlockResponse());
	}

	@Test
	public void testServiceProxyListenPort() {
		Assert.assertEquals(3000, rules.get(1).getKey().getPort());
	}

	@Test
	public void testServiceProxyTargetPort() {
		Assert.assertEquals(80, ((ServiceProxy)rules.get(1)).getTargetPort());
	}

	@Test
	public void testServiceProxyTargetHost() {
		Assert.assertEquals("thomas-bayer.com", ((ServiceProxy)rules.get(1)).getTargetHost());
	}

	@Test
	public void testServiceProxyDefaultMethod() {
		Assert.assertEquals("*", rules.get(1).getKey().getMethod());
	}

	@Test
	public void testTestServiceProxyDefaultHost() {
		Assert.assertEquals("*", rules.get(1).getKey().getHost());
	}

	@Test
	public void testServiceProxyBlockRequest() {
		Assert.assertFalse(rules.get(1).isBlockRequest());
	}

	@Test
	public void testServiceProxyBlockResponse() {
		Assert.assertTrue(rules.get(1).isBlockResponse());
	}

	@Test
	public void testLocalServiceProxyListenPort() {
		Assert.assertEquals(2000, rules.get(2).getKey().getPort());
	}

	@Test
	public void testLocalServiceProxyTargetPort() {
		assertEquals(3011, ((ServiceProxy)rules.get(2)).getTargetPort());
	}

	@Test
	public void testServiceProxyMethodSet() {
		assertEquals("GET", rules.get(2).getKey().getMethod());
	}

	@Test
	public void testServiceProxyHostSet() {
		assertEquals("localhost", rules.get(2).getKey().getHost());
	}

	@Test
	public void testLocalServiceProxyInboundSSL() {
		Assert.assertNull(rules.get(2).getSslInboundContext());
	}

	@Test
	public void testLocalServiceProxyOutboundSSL() {
		Assert.assertNull(rules.get(2).getSslOutboundContext());
	}

	@After
	public void tearDown() throws Exception {
		router.shutdown();
	}

}
