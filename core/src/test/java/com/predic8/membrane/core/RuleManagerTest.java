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
package com.predic8.membrane.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import com.predic8.membrane.core.rules.ProxyRule;
import com.predic8.membrane.core.rules.ProxyRuleKey;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.rules.ServiceProxy;
import com.predic8.membrane.core.rules.ServiceProxyKey;


public class RuleManagerTest {

	RuleManager manager;

	Rule proxy3013;

	Rule forwardBlz;

	Rule forwardBlzPOST;

	@Before
	public void setUp() throws Exception{
		manager = new RuleManager();
		MockRouter router = new MockRouter();
		manager.setRouter(router);
		proxy3013 = new ProxyRule(new ProxyRuleKey(3013));
		manager.addProxyAndOpenPortIfNew(proxy3013);

		forwardBlz = new ServiceProxy(new ServiceProxyKey("localhost", "*", ".*", 3014), "thomas-bayer.com", 80);
		forwardBlz.init(router);

		forwardBlzPOST = new ServiceProxy(new ServiceProxyKey("localhost", "POST", ".*", 3015), "thomas-bayer.com", 80);
		forwardBlzPOST.init(router);

		manager.addProxyAndOpenPortIfNew(forwardBlz);
		manager.addProxyAndOpenPortIfNew(forwardBlzPOST);
	}

	@Test
	public void testGetRules() throws Exception {
		assertFalse(manager.getRules().isEmpty());
		assertEquals(3, manager.getRules().size());
	}

	@Test
	public void testExists() throws Exception {
		assertTrue(manager.exists(proxy3013.getKey()));
	}

	@Test
	public void testGetMatchingRuleForwardBlz() throws Exception {
		assertEquals(forwardBlz, manager.getMatchingRule("localhost", "POST", "/axis2/services/blzservice", "1.1", 3014, null));
	}

	@Test
	public void testGetMatchingRuleForwardBlzPOST() throws Exception {
		assertEquals(forwardBlz, manager.getMatchingRule("localhost", "POST", "/axis2/services/blzservice", "1.1", 3014, null));
	}

	@Test
	public void testRemoveRule() throws Exception {
		manager.removeRule(proxy3013);
		assertEquals(2, manager.getRules().size());
		assertFalse(manager.getRules().contains(proxy3013));
	}

	@Test
	public void testRemoveAllRules() throws Exception {
		manager.removeAllRules();
		assertTrue(manager.getRules().isEmpty());
	}

	@Test
	public void testIsAnyRuleWithPort() throws Exception {
		assertFalse(manager.isAnyRuleWithPort(1234));
		assertTrue(manager.isAnyRuleWithPort(3013));
		assertTrue(manager.isAnyRuleWithPort(3014));
		assertTrue(manager.isAnyRuleWithPort(3015));
	}

	@Test
	public void testRuleUp() throws Exception {
		manager.ruleUp(forwardBlz);
		assertEquals(forwardBlz, manager.getRules().get(0));
	}

	@Test
	public void testRuleDown() throws Exception {
		manager.ruleDown(forwardBlz);
		assertEquals(forwardBlz, manager.getRules().get(2));
	}

}
