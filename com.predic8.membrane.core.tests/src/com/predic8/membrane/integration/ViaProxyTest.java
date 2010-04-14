package com.predic8.membrane.integration;

import java.io.InputStream;

import junit.framework.TestCase;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.InputStreamRequestEntity;
import org.apache.commons.httpclient.methods.PostMethod;
import org.junit.Before;
import org.junit.Test;

import com.predic8.membrane.core.Configuration;
import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.rules.ForwardingRule;
import com.predic8.membrane.core.rules.ForwardingRuleKey;
import com.predic8.membrane.core.rules.ProxyRule;
import com.predic8.membrane.core.rules.ProxyRuleKey;


public class ViaProxyTest extends TestCase {

	@Before
	public void setUp() throws Exception {
		HttpRouter proxy = new HttpRouter();
		proxy.getConfigurationManager().setConfiguration(new Configuration());
		
		proxy.getRuleManager().addRuleIfNew(new ProxyRule(new ProxyRuleKey(3128)));
		proxy.getTransport().openPort(3128);
		
		HttpRouter router = new HttpRouter();
		router.getRuleManager().addRuleIfNew(new ForwardingRule(new ForwardingRuleKey("localhost", "POST", ".*", 4000), "thomas-bayer.com", "80"));
		
		Configuration config = new Configuration();
		config.setUseProxy(true);
		config.setProxyHost("localhost");
		config.setProxyPort("3128");
		
		router.getConfigurationManager().setConfiguration(config);
		router.getTransport().openPort(4000);
	}
	
	@Test
	public void testPost() throws Exception {
		HttpClient client = new HttpClient();
		PostMethod post = new PostMethod("http://localhost:4000/axis2/services/BLZService");
		InputStream stream = this.getClass().getResourceAsStream("/getBank.xml");
		
		
		InputStreamRequestEntity entity = new InputStreamRequestEntity(stream);
		post.setRequestEntity(entity); 
		post.setRequestHeader("Content-Type", "text/xml;charset=UTF-8");
		post.setRequestHeader("SOAPAction", "");
		
		int status = client.executeMethod(post);
		System.out.println("Status Code: " + status);
	}
	
	
}
