package com.predic8.membrane.core.interceptor;

import junit.framework.TestCase;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.junit.Before;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.rules.ForwardingRule;
import com.predic8.membrane.core.rules.ForwardingRuleKey;
import com.predic8.membrane.core.rules.Rule;


public class RegExReplaceInterceptorTest extends TestCase {

	private Router router;
	
	@Before
	public void setUp() throws Exception {
		router = Router.init("resources/regex-monitor-beans.xml");
		Rule serverRule = new ForwardingRule(new ForwardingRuleKey("localhost", "*", ".*", 7000), "predic8.de", "80");
		router.getRuleManager().addRuleIfNew(serverRule);
	}
	
	@Override
	protected void tearDown() throws Exception {
		router.getTransport().closeAll();
	}
	
	public void testReplace() throws Exception {
		HttpClient client = new HttpClient();
		
		GetMethod method = new GetMethod("http://localhost:7000");
		method.setRequestHeader("Content-Type", "text/xml;charset=UTF-8");
		method.setRequestHeader("SOAPAction", "");
		
		assertEquals(200, client.executeMethod(method));
		
		assertTrue(new String(method.getResponseBody()).contains("Membrane RegEx Replacement Is Cool"));
	}
	
}
