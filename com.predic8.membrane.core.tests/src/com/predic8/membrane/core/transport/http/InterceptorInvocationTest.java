package com.predic8.membrane.core.transport.http;


import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
	
	List<String> backboneInterceptorNames;
	
	List<String> regularInterceptorNames;
	
	List<String> ruleInterceptorNames;
	
	List<String> interceptorSequence;
	
	@Before
	public void setUp() throws Exception {
		
		createInterceptorNames();
		
		createRouter();
		
		createInterceptorSequance();
		
		createPostMethod();
	}

	@After
	public void tearDown() throws Exception {
		router.getTransport().closeAll();
	}
	
	@Test
	public void testBla() throws Exception {
		callService();
		
		assertEquals(MockInterceptor.reqLabels.size(), MockInterceptor.respLabels.size());
		assertEquals(interceptorSequence, MockInterceptor.reqLabels);
		assertEquals(getReverseList(interceptorSequence), MockInterceptor.respLabels);
	}

	private ForwardingRule createForwardingRule() {
		ForwardingRule rule = new ForwardingRule(new ForwardingRuleKey("localhost", Request.METHOD_POST, "*", 4000), "thomas-bayer", 80);
		for (String label : ruleInterceptorNames) {
			rule.getInterceptors().add(new MockInterceptor(label));
		}
		return rule;
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
	
	private List<String> getReverseList(List<String> list) {
		Collections.reverse(list);
		return list;
	}
	
	private void createInterceptorSequance() {
		interceptorSequence = new ArrayList<String>();
		interceptorSequence.addAll(backboneInterceptorNames);
		interceptorSequence.addAll(regularInterceptorNames);
		interceptorSequence.addAll(ruleInterceptorNames);
	}

	private void createInterceptorNames() {
		ruleInterceptorNames = Arrays.asList(new String[] {"Rule 1", "Rule 2", "Rule 3"});
		backboneInterceptorNames = Arrays.asList(new String[] {"TR Backbone 1", "TR Backbone 2", "TR Backbone 3" });
		regularInterceptorNames = Arrays.asList(new String[] {"TR Normal 1", "TR Normal 2", "TR Normal 3", "TR Normal 4" });
	}

	private void createRouter() throws IOException {
		router = new HttpRouter();
		router.getRuleManager().addRuleIfNew(createForwardingRule());
		addMockInterceptors(router.getTransport().getBackboneInterceptors(), backboneInterceptorNames);
		addMockInterceptors(router.getTransport().getInterceptors(), regularInterceptorNames);
	}

	private void addMockInterceptors(List<Interceptor> list, List<String> labels) {
		for (String label : labels) {
			list.add(new MockInterceptor(label));
		}
	}
}
