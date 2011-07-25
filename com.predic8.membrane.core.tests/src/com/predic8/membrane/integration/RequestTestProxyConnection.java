package com.predic8.membrane.integration;

import static org.junit.Assert.assertEquals;

import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.rules.ProxyRule;
import com.predic8.membrane.core.rules.ProxyRuleKey;
import com.predic8.membrane.core.rules.Rule;

//TODO delete ? integrate into test suite ?
public class RequestTestProxyConnection {

	HttpRouter router;
	
	@Before
	public void setUp() throws Exception {
		router = new HttpRouter();
		Rule proxy = new ProxyRule(new ProxyRuleKey(12000));
		router.getRuleManager().addRuleIfNew(proxy);
		
	}
	
	@Test
	public void testS() throws Exception {
		
		
		HttpClient client = new HttpClient();
		client.getHostConfiguration().setProxy("localhost", 9000);
		
		
		GetMethod get = new GetMethod("http://presa.ge/new/img/blog/blogger24m.jpg");
		get.addRequestHeader(new Header("Keep-Alive", "115"));
		get.addRequestHeader(new Header("Proxy-Connection", "keep-alive"));
		
		int status = client.executeMethod(get);
		
		Header[] headers = get.getResponseHeaders();
		for (Header header : headers) {
			System.err.println(header.getName() + ":  " + header.getValue());
		}
		
		assertEquals(4635, get.getResponseBody().length);
		assertEquals(200, status);
	}
	
	@After
	public void tearDown() throws Exception {
		router.getTransport().closeAll();
		
	}
	
}
