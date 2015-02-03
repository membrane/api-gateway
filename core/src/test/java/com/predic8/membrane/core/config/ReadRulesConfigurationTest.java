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

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.junit.Assert;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.rules.ServiceProxy;

public class ReadRulesConfigurationTest {

	private Router router;
	
	private List<Rule> rules;
	
	@Before
	public void setUp() throws Exception {
		router = Router.init("src/test/resources/proxies.xml");
		rules = router.getRuleManager().getRules();
	}
	
	@Test
	public void testRulesSize() throws Exception {
	 	Assert.assertEquals(3, rules.size());
	}

	@Test
	public void testProxyRuleListenPort() throws Exception {
		Assert.assertEquals(3090, rules.get(0).getKey().getPort());
	}
	
	@Test
	public void testProxyRuleBlockRequest() throws Exception {
		Assert.assertEquals(true, rules.get(0).isBlockRequest());
	}
	
	@Test
	public void testProxyRuleBlockResponse() throws Exception {
		Assert.assertEquals(false, rules.get(0).isBlockResponse());
	}
	
	@Test
	public void testServiceProxyListenPort() throws Exception {
		Assert.assertEquals(3000, ((ServiceProxy)rules.get(1)).getKey().getPort());
	}
	
	@Test
	public void testServiceProxyTargetPort() throws Exception {
		Assert.assertEquals(80, ((ServiceProxy)rules.get(1)).getTargetPort());
	}
	
	@Test
	public void testServiceProxyTargetHost() throws Exception {
		Assert.assertEquals("thomas-bayer.com", ((ServiceProxy)rules.get(1)).getTargetHost());
	}
	
	@Test
	public void testServiceProxyDefaultMethod() throws Exception {
		Assert.assertEquals("*", ((ServiceProxy)rules.get(1)).getKey().getMethod());
	}
	
	@Test
	public void testTestServiceProxyDefaultHost() throws Exception {
		Assert.assertEquals("*", ((ServiceProxy)rules.get(1)).getKey().getHost());
	}
	
	@Test
	public void testServiceProxyBlockRequest() throws Exception {
		Assert.assertEquals(false, ((ServiceProxy)rules.get(1)).isBlockRequest());
	}
	
	@Test
	public void testServiceProxyBlockResponse() throws Exception {
		Assert.assertEquals(true, ((ServiceProxy)rules.get(1)).isBlockResponse());
	}
	
	@Test
	public void testLocalServiceProxyListenPort() throws Exception {
		Assert.assertEquals(2000, ((ServiceProxy)rules.get(2)).getKey().getPort());
	}
	
	@Test
	public void testLocalServiceProxyTargetPort() throws Exception {
		assertEquals(3011, ((ServiceProxy)rules.get(2)).getTargetPort());
	}
	
	@Test
	public void testServiceProxyMethodSet() throws Exception {
		assertEquals("GET", ((ServiceProxy)rules.get(2)).getKey().getMethod());
	}
	
	@Test
	public void testServiceProxyHostSet() throws Exception {
		assertEquals("localhost", ((ServiceProxy)rules.get(2)).getKey().getHost());
	}
	
	@Test
	public void testLocalServiceProxyInboundSSL() throws Exception {
		Assert.assertNull(((ServiceProxy)rules.get(2)).getSslInboundContext());
	}
	
	@Test
	public void testLocalServiceProxyOutboundSSL() throws Exception {
		Assert.assertNull(((ServiceProxy)rules.get(2)).getSslOutboundContext());
	}
	
	@After
	public void tearDown() throws Exception {
		router.shutdown();
	}

}
