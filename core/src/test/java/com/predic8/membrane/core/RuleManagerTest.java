/* Copyright 2009, 2012, 2024 predic8 GmbH, www.predic8.com

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

import com.predic8.membrane.core.rules.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.UnknownHostException;

import static com.predic8.membrane.util.TestUtil.assembleExchange;
import static org.junit.jupiter.api.Assertions.*;

public class RuleManagerTest {

	RuleManager manager;
	Rule proxy3013;
	Rule forwardBlz;
	Rule forwardBlzPOST;

	@BeforeEach
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
	public void testGetRules() {
		assertFalse(manager.getRules().isEmpty());
		assertEquals(3, manager.getRules().size());
	}

	@Test
	public void testExists() {
		assertTrue(manager.exists(proxy3013.getKey()));
	}

	@Test
	public void testGetMatchingRuleForwardBlz() throws UnknownHostException {
		assertEquals(forwardBlz, manager.getMatchingRule(assembleExchange("localhost", "POST", "/axis2/services/blzservice", "1.1", 3014, "127.0.0.1")));
	}

	@Test
	public void testGetMatchingRuleForwardBlzPOST() throws UnknownHostException {
		assertEquals(forwardBlz, manager.getMatchingRule(assembleExchange("localhost", "POST", "/axis2/services/blzservice", "1.1", 3014, "127.0.0.1")));
	}

	@Test
	public void testRemoveRule() {
		manager.removeRule(proxy3013);
		assertEquals(2, manager.getRules().size());
		assertFalse(manager.getRules().contains(proxy3013));
	}

	@Test
	public void testRemoveAllRules() {
		manager.removeAllRules();
		assertTrue(manager.getRules().isEmpty());
	}

	@Test
	public void testIsAnyRuleWithPort() {
		assertFalse(manager.isAnyRuleWithPort(1234));
		assertTrue(manager.isAnyRuleWithPort(3013));
		assertTrue(manager.isAnyRuleWithPort(3014));
		assertTrue(manager.isAnyRuleWithPort(3015));
	}

	@Test
	public void testRuleUp() {
		manager.ruleUp(forwardBlz);
		assertEquals(forwardBlz, manager.getRules().get(0));
	}

	@Test
	public void testRuleDown() {
		manager.ruleDown(forwardBlz);
		assertEquals(forwardBlz, manager.getRules().get(2));
	}
}
