package com.predic8.membrane.core.config;

import static org.junit.Assert.assertEquals;

import java.util.List;

import junit.framework.Assert;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.rules.ForwardingRule;
import com.predic8.membrane.core.rules.Rule;

public class ReadRulesConfigurationTest {

	@Test
	public void testReadRules() throws Exception {
		Router router = new HttpRouter();
		router.getConfigurationManager().loadConfiguration("resources/rules.xml");
		
	 	List<Rule> rules = router.getRuleManager().getRules();
	 	Assert.assertEquals(3, rules.size());
	
	 	Rule proxyRule = rules.get(0);
	 	Assert.assertEquals(9000, proxyRule.getKey().getPort());
	 	
	 	Rule externalRule = rules.get(1);
	 	Assert.assertEquals("thomas-bayer.com", ((ForwardingRule)externalRule).getTargetHost());
	 	
		Rule localRule = rules.get(2);
	 	assertEquals(8080, ((ForwardingRule)localRule).getTargetPort());
	 	router.getTransport().closeAll();
	}

	@Test
	public void testReadRulesWithInterceptor() throws Exception {
		
		Router router = Router.init("classpath:/monitor-beans.xml");
		router.getConfigurationManager().loadConfiguration("classpath:/rules-interceptor-ref.xml");
		
	 	List<Rule> rules = router.getRuleManager().getRules();
	 	Assert.assertEquals(1, rules.size());
	
	 	ForwardingRule fRule = (ForwardingRule) rules.get(0);
	 	Interceptor i = fRule.getInterceptors().get(0);
	 	assertEquals("roundRobinBalancer", i.getDisplayName());
	 	org.junit.Assert.assertNotNull(i.getRouter());
	 	router.getTransport().closeAll();
	}
	
	@After
	public void tearDown() throws Exception {
		
	}

}
