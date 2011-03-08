package com.predic8.membrane.core.http;

import junit.framework.TestCase;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.DeleteMethod;

import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.interceptor.MockHttpServerInterceptor;
import com.predic8.membrane.core.rules.ForwardingRule;
import com.predic8.membrane.core.rules.ForwardingRuleKey;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.util.MessageUtil;

public class MethodTest extends TestCase {

	private HttpRouter router;
	
	private HttpRouter server;
	
	@Override
	protected void setUp() throws Exception {
		Rule rule = new ForwardingRule(new ForwardingRuleKey("localhost", "*", ".*", 4000), "localhost", "7000");
		router = new HttpRouter();
		router.getRuleManager().addRuleIfNew(rule);
	
		Rule serverRule = new ForwardingRule(new ForwardingRuleKey("localhost", "*", ".*", 7000), "predic8.de", "80");
		serverRule.getInterceptors().add(new MockHttpServerInterceptor());
		server = new HttpRouter();
		server.getRuleManager().addRuleIfNew(serverRule);
	}
	
	public void testDELETE() throws Exception {
		HttpClient client = new HttpClient();
		
		DeleteMethod delete = new DeleteMethod("http://localhost:4000/aaaa");
		delete.setRequestHeader("Content-Type", "text/xml;charset=UTF-8");
		delete.setRequestHeader("SOAPAction", "");
		
		int status = client.executeMethod(delete);
		System.out.println(status);
	}
		
	@Override
	protected void tearDown() throws Exception {
		router.getTransport().closeAll();
		server.getTransport().closeAll();
	}

}
