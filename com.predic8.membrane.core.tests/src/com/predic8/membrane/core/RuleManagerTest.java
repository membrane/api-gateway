package com.predic8.membrane.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import com.predic8.membrane.core.rules.ForwardingRule;
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
		
		forwardBlz = new ForwardingRule(new ForwardingRuleKey("localhost", "*", ".*", 5001), "thomas-bayer.com", 80);
		
		forwardBlzPOST = new ForwardingRule(new ForwardingRuleKey("localhost", "POST", ".*", 5002), "thomas-bayer.com", 80);
		
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
