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
package com.predic8.membrane.interceptor;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpVersion;
import org.apache.commons.httpclient.methods.InputStreamRequestEntity;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.http.params.HttpProtocolParams;
import org.junit.Test;

import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.exchange.HttpExchange;
import com.predic8.membrane.core.interceptor.balancer.ByThreadStrategy;
import com.predic8.membrane.core.interceptor.balancer.DispatchingStrategy;
import com.predic8.membrane.core.interceptor.balancer.LoadBalancingInterceptor;
import com.predic8.membrane.core.interceptor.balancer.RoundRobinStrategy;
import com.predic8.membrane.core.rules.ForwardingRule;
import com.predic8.membrane.core.rules.ForwardingRuleKey;
import com.predic8.membrane.core.services.DummyWebServiceInterceptor;

public class LoadBalancingInterceptorTest extends TestCase {

	private DummyWebServiceInterceptor mockInterceptor1;

	private DummyWebServiceInterceptor mockInterceptor2;

	private LoadBalancingInterceptor balancingInterceptor;

	private DispatchingStrategy roundRobinStrategy;

	private DispatchingStrategy byThreadStrategy;

	private String endPoint1 = "localhost:2000";

	private String endPoint2 = "localhost:3000";

	private HttpRouter service1;
	private HttpRouter service2;

	private HttpRouter balancer;

	@Override
	protected void setUp() throws Exception {

		service1 = new HttpRouter();
		mockInterceptor1 = new DummyWebServiceInterceptor();
		service1.getTransport().getInterceptors().add(mockInterceptor1);
		service1.getRuleManager().addRuleIfNew(new ForwardingRule(new ForwardingRuleKey("localhost", "POST", ".*", 2000), "thomas-bayer.com", "80"));

		service2 = new HttpRouter();
		mockInterceptor2 = new DummyWebServiceInterceptor();
		service2.getTransport().getInterceptors().add(mockInterceptor2);
		service2.getRuleManager().addRuleIfNew(new ForwardingRule(new ForwardingRuleKey("localhost", "POST", ".*", 3000), "thomas-bayer.com", "80"));

		balancingInterceptor = new LoadBalancingInterceptor();
		List<String> endpoints = new ArrayList<String>();
		endpoints.add(endPoint1);
		endpoints.add(endPoint2);
		balancingInterceptor.setEndpoints(endpoints);

		balancer = new HttpRouter();
		balancer.getRuleManager().addRuleIfNew(new ForwardingRule(new ForwardingRuleKey("localhost", "POST", ".*", 7000), "thomas-bayer.com", "80"));
		balancer.getTransport().getInterceptors().add(balancingInterceptor);

		roundRobinStrategy = new RoundRobinStrategy();
		byThreadStrategy = new ByThreadStrategy();

		super.setUp();

	}

	@Override
	protected void tearDown() throws Exception {
		service1.getTransport().closeAll();
		service2.getTransport().closeAll();
		balancer.getTransport().closeAll();
		super.tearDown();
	}

	@Test
	public void testGetDestinationURLWithHostname() throws MalformedURLException {
		doTestGetDestinationURL("http://localhost/axis2/services/BLZService?wsdl", "http://thomas-bayer.com:80/axis2/services/BLZService?wsdl");

	}

	@Test
	public void testGetDestinationURLWithoutHostname() throws MalformedURLException {
		doTestGetDestinationURL("/axis2/services/BLZService?wsdl", "http://thomas-bayer.com:80/axis2/services/BLZService?wsdl");
	}

	private void doTestGetDestinationURL(String requestUri, String expectedUri) throws MalformedURLException {
		Exchange exc = new HttpExchange();
		exc.setOriginalRequestUri(requestUri);
		assertEquals(expectedUri, balancingInterceptor.getDestinationURL("thomas-bayer.com:80", exc));
	}

	@Test
	public void testRoundRobinDispachingStrategy() throws Exception {
		balancingInterceptor.setDispatchingStrategy(roundRobinStrategy);

		HttpClient client = new HttpClient();
		client.getParams().setParameter(HttpProtocolParams.PROTOCOL_VERSION, HttpVersion.HTTP_1_1);

		PostMethod vari = getPostMethod();
		int status = client.executeMethod(vari);
		System.out.println(new String(vari.getResponseBody()));
		
		assertEquals(200, status);
		assertEquals(1, mockInterceptor1.counter);
		assertEquals(0, mockInterceptor2.counter);

		assertEquals(200, client.executeMethod(getPostMethod()));
		assertEquals(1, mockInterceptor1.counter);
		assertEquals(1, mockInterceptor2.counter);

		assertEquals(200, client.executeMethod(getPostMethod()));
		assertEquals(2, mockInterceptor1.counter);
		assertEquals(1, mockInterceptor2.counter);

		assertEquals(200, client.executeMethod(getPostMethod()));
		assertEquals(2, mockInterceptor1.counter);
		assertEquals(2, mockInterceptor2.counter);
	}

	private PostMethod getPostMethod() {
		PostMethod post = new PostMethod("http://localhost:7000/axis2/services/BLZService");
		post.setRequestEntity(new InputStreamRequestEntity(this.getClass().getResourceAsStream("/getBank.xml")));
		post.setRequestHeader("Content-Type", "text/xml;charset=UTF-8");
		post.setRequestHeader("SOAPAction", "");

		return post;
	}

	public void testFailOver() throws Exception {
		balancingInterceptor.setDispatchingStrategy(roundRobinStrategy);

		HttpClient client = new HttpClient();
		client.getParams().setParameter(HttpProtocolParams.PROTOCOL_VERSION, HttpVersion.HTTP_1_1);

		assertEquals(200, client.executeMethod(getPostMethod()));
		assertEquals(1, mockInterceptor1.counter);
		assertEquals(0, mockInterceptor2.counter);

		assertEquals(200, client.executeMethod(getPostMethod()));
		assertEquals(1, mockInterceptor1.counter);
		assertEquals(1, mockInterceptor2.counter);

		service1.getTransport().closeAll();

		// TODO may be close connection 
		Thread.sleep(32000);
		
		assertEquals(200, client.executeMethod(getPostMethod()));
		assertEquals(1, mockInterceptor1.counter);
		assertEquals(2, mockInterceptor2.counter);

		assertEquals(200, client.executeMethod(getPostMethod()));
		assertEquals(3, mockInterceptor2.counter);

	}

	@Test
	public void testByThreadStrategy() throws Exception {
		balancingInterceptor.setDispatchingStrategy(byThreadStrategy);

	}
}
