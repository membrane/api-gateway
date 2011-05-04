package com.predic8.membrane.core;

import java.util.List;

import junit.framework.Assert;

import org.junit.Test;

import com.predic8.membrane.core.rules.ForwardingRule;
import com.predic8.membrane.core.rules.Rule;

public class ReadRulesInterceptorRefTest {


	@Test
	public void testReadRules() throws Exception {
		
		Router router = Router.init("classpath:/monitor-beans.xml");
		router.getConfigurationManager().loadConfiguration("classpath:/rules-interceptor-ref.xml");
		
	 	List<Rule> rules = router.getRuleManager().getRules();
	 	Assert.assertEquals(1, rules.size());
	
	 	ForwardingRule fRule = (ForwardingRule) rules.get(0);
	 	Assert.assertEquals("roundRobinBalancer", fRule.getInterceptors().get(0).getDisplayName());
	 	
	 	router.getTransport().closeAll();
	}

}
