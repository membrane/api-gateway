package com.predic8.membrane.core.rules;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import com.predic8.membrane.core.http.Request;


public class ForwardingRuleKeyTest {

	
	@Before
	public void setUp() throws Exception {
			
	}
	
	public void testNoRegExpMatchesPath() throws Exception {
		ForwardingRuleKey key = new ForwardingRuleKey("localhost", Request.METHOD_POST, "/axis2/services", 3000);
		key.setPathRegExp(false);
		
		assertTrue(key.matchesPath("/axis2/services/bla/other"));
		assertFalse(key.matchesPath("/service/we"));
	}
	
	@Test
	public void testRegularExpressionMatchesPath() throws Exception {
		ForwardingRuleKey key = new ForwardingRuleKey("localhost", Request.METHOD_POST, ".*FooService", 3000);
		assertTrue(key.matchesPath("/axis2/services/FooService"));
		assertFalse(key.matchesPath("/axis2/services/FooService/Bla")); //???
	}
	
	@Test
	public void testRegularExpressionMatchesPathAnyURI() throws Exception {
		ForwardingRuleKey key = new ForwardingRuleKey("localhost", Request.METHOD_POST, ".*", 3000);
		assertTrue(key.matchesPath("/axis2/services/FooService"));
		assertTrue(key.matchesPath("/axis2/services/FooService/Bla"));
	}
}
