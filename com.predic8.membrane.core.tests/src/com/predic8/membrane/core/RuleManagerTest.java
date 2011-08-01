/* Copyright 2009 predic8 GmbH, www.predic8.com

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

import com.predic8.membrane.core.rules.ServiceProxy;
import com.predic8.membrane.core.rules.ForwardingRuleKey;
import com.predic8.membrane.core.rules.ProxyRule;
import com.predic8.membrane.core.rules.ProxyRuleKey;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.rules.RuleKey;


public class RuleManagerTest {

	RuleManager manager;
	
	Rule proxy5000;
	
	Rule forwardBlz;
	
	Rule forwardBlzPOST;
	
	@Before
	public void setUp() throws Exception{
		manager = new RuleManager();
		manager.setRouter(new MockRouter());
		proxy5000 = new ProxyRule(new ProxyRuleKey(5000));
		manager.addRuleIfNew(proxy5000);
		
		forwardBlz = new ServiceProxy(new ForwardingRuleKey("localhost", "*", ".*", 5001), "thomas-bayer.com", 80);
		
		forwardBlzPOST = new ServiceProxy(new ForwardingRuleKey("localhost", "POST", ".*", 5002), "thomas-bayer.com", 80);
		
		manager.addRuleIfNew(forwardBlz);
		manager.addRuleIfNew(forwardBlzPOST);
	}
	
	@Test
	public void testGetRules() throws Exception {
		assertFalse(manager.getRules().isEmpty());
		assertEquals(3, manager.getRules().size());
	}
	
	@Test
	public void testExists() throws Exception {
		assertTrue(manager.exists(proxy5000.getKey()));
	}
	
	@Test
	public void testGetMatchingRuleForwardBlz() throws Exception {
		RuleKey key = new ForwardingRuleKey("localhost", "POST", "/axis2/services/blzservice", 5001);
		assertEquals(forwardBlz, manager.getMatchingRule(key));
	}
	
	@Test
	public void testGetMatchingRuleForwardBlzPOST() throws Exception {
		RuleKey key = new ForwardingRuleKey("localhost", "POST", "/axis2/services/blzservice", 5001);
		assertEquals(forwardBlz, manager.getMatchingRule(key));
	}
	
	@Test
	public void testRemoveRule() throws Exception {
		manager.removeRule(proxy5000);
		assertEquals(2, manager.getRules().size());
		assertFalse(manager.getRules().contains(proxy5000));
	}
	
	@Test
	public void testRemoveAllRules() throws Exception {
		manager.removeAllRules();
		assertTrue(manager.getRules().isEmpty());
	}
	
	@Test
	public void testIsAnyRuleWithPort() throws Exception {
		assertFalse(manager.isAnyRuleWithPort(1234));
		assertTrue(manager.isAnyRuleWithPort(5000));
		assertTrue(manager.isAnyRuleWithPort(5001));
		assertTrue(manager.isAnyRuleWithPort(5002));
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
