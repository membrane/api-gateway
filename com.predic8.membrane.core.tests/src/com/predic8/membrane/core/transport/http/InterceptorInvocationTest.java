package com.predic8.membrane.core.transport.http;


import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.methods.InputStreamRequestEntity;
import org.apache.commons.httpclient.methods.PostMethod;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.interceptor.MockInterceptor;
import com.predic8.membrane.core.rules.ForwardingRule;
import com.predic8.membrane.core.rules.ForwardingRuleKey;

public class InterceptorInvocationTest {

	private HttpRouter router;
	
	private PostMethod post;
	
	@Before
	public void setUp() throws Exception {
		
		router = new HttpRouter();
		
		addMockInterceptors(router.getTransport().getBackboneInterceptors(), new String[] {"TR Backbone 1", "TR Backbone 2", "TR Backbone 3" });
		addMockInterceptors(router.getTransport().getInterceptors(), new String[] {"TR Normal 1", "TR Normal 2", "TR Normal 3", "TR Normal 4" });
		
		createPostMethod();
	}

	private void addMockInterceptors(List<Interceptor> list, String[] labels) {
		for (String label : labels) {
			list.add(new MockInterceptor(label));
		}
	}
	
	@After
	public void tearDown() throws Exception {
		router.getTransport().closeAll();
	}
	
	@Test
	public void testBla() throws Exception {
		ForwardingRule rule = new ForwardingRule(new ForwardingRuleKey("localhost", Request.METHOD_POST, "*", 4000), "thomas-bayer", 80);
		rule.getInterceptors().add(new MockInterceptor("Rule 1"));
		rule.getInterceptors().add(new MockInterceptor("Rule 2"));
		rule.getInterceptors().add(new MockInterceptor("Rule 3"));
		router.getRuleManager().addRuleIfNew(rule);
		
		callService();
		
		assertEquals(MockInterceptor.reqLabels.size(), MockInterceptor.respLabels.size());
		
		List<String> reqLabels =  MockInterceptor.reqLabels;
		assertEquals("TR Backbone 1", reqLabels.get(0));
		assertEquals("TR Backbone 2", reqLabels.get(1));
		assertEquals("TR Backbone 3", reqLabels.get(2));
		
		assertEquals("TR Normal 1", reqLabels.get(3));
		assertEquals("TR Normal 2", reqLabels.get(4));
		assertEquals("TR Normal 3", reqLabels.get(5));
		assertEquals("TR Normal 4", reqLabels.get(6));
		
		assertEquals("Rule 1", reqLabels.get(7));
		assertEquals("Rule 2", reqLabels.get(8));
		assertEquals("Rule 3", reqLabels.get(9));
		
		List<String> respLabels =  MockInterceptor.respLabels;
		assertEquals("Rule 3", respLabels.get(0));
		assertEquals("Rule 2", respLabels.get(1));
		assertEquals("Rule 1", respLabels.get(2));
		
		assertEquals("TR Normal 4", respLabels.get(3));
		assertEquals("TR Normal 3", respLabels.get(4));
		assertEquals("TR Normal 2", respLabels.get(5));
		assertEquals("TR Normal 1", respLabels.get(6));
		
		assertEquals("TR Backbone 3", respLabels.get(7));
		assertEquals("TR Backbone 2", respLabels.get(8));
		assertEquals("TR Backbone 1", respLabels.get(9));
	}
	
	
	private void callService() throws HttpException, IOException {
		HttpClient client = new HttpClient();
		client.executeMethod(post);
	}
	
	private void createPostMethod() {
		post = new PostMethod("http://localhost:4000/axis2/services/BLZService");
		InputStream stream = this.getClass().getResourceAsStream("/getBank.xml");
		
		InputStreamRequestEntity entity = new InputStreamRequestEntity(stream);
		post.setRequestEntity(entity); 
		post.setRequestHeader("Content-Type", "text/xml;charset=UTF-8");
		post.setRequestHeader("SOAPAction", "");
	}
}
