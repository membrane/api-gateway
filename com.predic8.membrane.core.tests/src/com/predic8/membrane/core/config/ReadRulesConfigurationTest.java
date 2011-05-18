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
package com.predic8.membrane.core.config;

import static org.junit.Assert.assertEquals;

import java.util.List;

import junit.framework.Assert;

import org.junit.After;
import org.junit.Test;

import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.Router;
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
