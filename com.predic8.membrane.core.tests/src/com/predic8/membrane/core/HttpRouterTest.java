package com.predic8.membrane.core;

import java.util.List;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.Test;

import com.predic8.membrane.core.rules.ForwardingRule;
import com.predic8.membrane.core.rules.Rule;


public class HttpRouterTest {

	private HttpRouter router; 
	
	@Before
	public void setUp() throws Exception {
		router = new HttpRouter();
	}
	
	@Test
	public void testLoadConfiguration() throws Exception {
		router.getConfigurationManager().loadConfiguration("resources/rules.xml");
		List<Rule> rules = router.getRuleManager().getRules();
	 	Assert.assertEquals(3, rules.size());
	
	 	Rule proxyRule = rules.get(0);
	 	Assert.assertEquals(9000, proxyRule.getKey().getPort());
	 	
	 	Rule externalRule = rules.get(1);
	 	Assert.assertEquals("thomas-bayer.com", ((ForwardingRule)externalRule).getTargetHost());
	 	
		Rule localRule = rules.get(2);
	 	Assert.assertEquals(8080, ((ForwardingRule)localRule).getTargetPort());
	}
	
	
	
}
