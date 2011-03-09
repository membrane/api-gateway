package com.predic8.membrane.core.interceptor.rewrite;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpVersion;
import org.apache.commons.httpclient.methods.InputStreamRequestEntity;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.http.params.HttpProtocolParams;
import org.junit.Test;

import junit.framework.TestCase;

import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.rules.ForwardingRule;
import com.predic8.membrane.core.rules.ForwardingRuleKey;
import com.predic8.membrane.core.rules.Rule;

public class SimpleURLRewriteInterceptorIntegrationTest extends TestCase {

	private static HttpRouter router;

	private SimpleURLRewriteInterceptor  interceptor; 
	
	@Override
	protected void setUp() throws Exception {
		Rule rule = new ForwardingRule(new ForwardingRuleKey("localhost", "POST", ".*", 8000), "thomas-bayer.com", "80");
		router = new HttpRouter();
		router.getRuleManager().addRuleIfNew(rule);
		
		interceptor = new SimpleURLRewriteInterceptor();
		Map<String, String> mapping = new HashMap<String, String>();
		mapping.put("/blz-service?wsdl", "/axis2/services/BLZService?wsdl");
		interceptor.setMapping(mapping );
		
		router.getTransport().getInterceptors().add(0, interceptor);
	}

	@Override
	protected void tearDown() throws Exception {
		router.getTransport().closeAll();
	}

	@Test
	public void testRewriting() throws Exception {
		HttpClient client = new HttpClient();
		client.getParams().setParameter(HttpProtocolParams.PROTOCOL_VERSION, HttpVersion.HTTP_1_1);
		int status = client.executeMethod(getPostMethod());
	
	    assertEquals(200, status);
	}
	
	private PostMethod getPostMethod() {
		PostMethod post = new PostMethod("http://localhost:8000/blz-service?wsdl");
		post.setRequestEntity(new InputStreamRequestEntity(this.getClass().getResourceAsStream("/getBank.xml")));
		post.setRequestHeader("Content-Type", "text/xml;charset=UTF-8");
		post.setRequestHeader("SOAPAction", "");

		return post;
	}

	
}
