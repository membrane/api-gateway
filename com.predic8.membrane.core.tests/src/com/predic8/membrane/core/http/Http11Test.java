package com.predic8.membrane.core.http;

import java.io.InputStream;

import junit.framework.TestCase;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.InputStreamRequestEntity;
import org.apache.commons.httpclient.methods.PostMethod;
import org.junit.Before;
import org.junit.Test;

import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.rules.ForwardingRule;
import com.predic8.membrane.core.rules.ForwardingRuleKey;
import com.predic8.membrane.core.rules.Rule;


public class Http11Test extends TestCase {

	@Before
	public void setUp() throws Exception {
		Rule rule = new ForwardingRule(new ForwardingRuleKey("localhost", "POST", ".*", 4000), "thomas-bayer.com", "80");
		HttpRouter router = new HttpRouter();
		router.getRuleManager().addRuleIfNew(rule);
		
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
		
		int status = client.executeMethod(post);
		System.out.println("Status Code: " + status);
	}
	
}
