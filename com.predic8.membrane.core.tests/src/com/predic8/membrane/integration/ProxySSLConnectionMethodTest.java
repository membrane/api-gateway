package com.predic8.membrane.integration;

import static org.junit.Assert.assertEquals;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.PostMethod;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchangestore.MemoryExchangeStore;
import com.predic8.membrane.core.rules.ProxyRule;
import com.predic8.membrane.core.rules.ProxyRuleKey;

public class ProxySSLConnectionMethodTest {

	private Router router;
	
	@Before
	public void setUp() throws Exception {
		router = new HttpRouter();
		router.setExchangeStore(new MemoryExchangeStore());
		router.getRuleManager().addRuleIfNew(new ProxyRule(new ProxyRuleKey(3128)));
	}
	
	@After
	public void tearDown() throws Exception {
		router.getTransport().closeAll();
	}
	
	@SuppressWarnings("deprecation")
	@Test
	public void testSSLConnectionMethod() throws Exception {
		HttpClient client = new HttpClient();
		client.getHostConfiguration().setProxy("localhost", 3128);
	
		PostMethod post = new PostMethod("https://predic8.com/svn/membrane/monitor/");
		client.executeMethod(post);
		assertEquals("Subversion Repositories", post.getAuthenticationRealm());
	}
	
}
