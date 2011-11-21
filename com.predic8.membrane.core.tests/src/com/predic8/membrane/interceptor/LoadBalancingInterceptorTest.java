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

import static junit.framework.Assert.assertEquals;

import java.net.MalformedURLException;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpVersion;
import org.apache.commons.httpclient.methods.InputStreamRequestEntity;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.http.params.HttpProtocolParams;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.MimeType;
import com.predic8.membrane.core.interceptor.balancer.ByThreadStrategy;
import com.predic8.membrane.core.interceptor.balancer.ClusterManager;
import com.predic8.membrane.core.interceptor.balancer.DispatchingStrategy;
import com.predic8.membrane.core.interceptor.balancer.LoadBalancingInterceptor;
import com.predic8.membrane.core.interceptor.balancer.Node;
import com.predic8.membrane.core.interceptor.balancer.RoundRobinStrategy;
import com.predic8.membrane.core.rules.ServiceProxy;
import com.predic8.membrane.core.rules.ServiceProxyKey;
import com.predic8.membrane.core.services.DummyWebServiceInterceptor;
import com.predic8.membrane.integration.Http11Test;

public class LoadBalancingInterceptorTest {

	private DummyWebServiceInterceptor mockInterceptor1;
	private DummyWebServiceInterceptor mockInterceptor2;
	protected LoadBalancingInterceptor balancingInterceptor;
	private DispatchingStrategy roundRobinStrategy;
	private DispatchingStrategy byThreadStrategy;
	private HttpRouter service1;
	private HttpRouter service2;
	protected HttpRouter balancer;

	@Before
	public void setUp() throws Exception {

		service1 = new HttpRouter();
		mockInterceptor1 = new DummyWebServiceInterceptor();
		ServiceProxy sp1 = new ServiceProxy(new ServiceProxyKey("localhost",
				"POST", ".*", 2000), "thomas-bayer.com", 80);
		sp1.getInterceptors().add(mockInterceptor1);
		service1.getRuleManager().addRuleIfNew(sp1);

		service2 = new HttpRouter();
		mockInterceptor2 = new DummyWebServiceInterceptor();
		ServiceProxy sp2 = new ServiceProxy(new ServiceProxyKey("localhost",
				"POST", ".*", 3000), "thomas-bayer.com", 80);
		sp2.getInterceptors().add(mockInterceptor2);
		service2.getRuleManager().addRuleIfNew(sp2);

		ClusterManager cm = new ClusterManager();
		cm.up("Default", "localhost", 2000);
		cm.up("Default", "localhost", 3000);

		balancer = new HttpRouter();
		balancer.setClusterManager(cm);
		ServiceProxy sp3 = new ServiceProxy(new ServiceProxyKey("localhost",
				"POST", ".*", 7000), "thomas-bayer.com", 80);
		balancingInterceptor = new LoadBalancingInterceptor();
		sp3.getInterceptors().add(balancingInterceptor);
		balancer.getRuleManager().addRuleIfNew(sp3);
		balancingInterceptor.setRouter(balancer);

		roundRobinStrategy = new RoundRobinStrategy();
		byThreadStrategy = new ByThreadStrategy();
	}

	@After
	public void tearDown() throws Exception {
		service1.getTransport().closeAll();
		service2.getTransport().closeAll();
		balancer.getTransport().closeAll();
	}

	@Test
	public void testGetDestinationURLWithHostname()
			throws MalformedURLException {
		doTestGetDestinationURL(
				"http://localhost/axis2/services/BLZService?wsdl",
				"http://thomas-bayer.com:80/axis2/services/BLZService?wsdl");
	}

	@Test
	public void testGetDestinationURLWithoutHostname()
			throws MalformedURLException {
		doTestGetDestinationURL("/axis2/services/BLZService?wsdl",
				"http://thomas-bayer.com:80/axis2/services/BLZService?wsdl");
	}

	private void doTestGetDestinationURL(String requestUri, String expectedUri)
			throws MalformedURLException {
		Exchange exc = new Exchange();
		exc.setOriginalRequestUri(requestUri);
		assertEquals(expectedUri, balancingInterceptor.getDestinationURL(
				new Node("thomas-bayer.com", 80), exc));
	}

	@Test
	public void testRoundRobinDispachingStrategy() throws Exception {
		balancingInterceptor.setDispatchingStrategy(roundRobinStrategy);

		HttpClient client = new HttpClient();
		client.getParams().setParameter(HttpProtocolParams.PROTOCOL_VERSION,
				HttpVersion.HTTP_1_1);

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

	@Test
	public void testExpect100Continue() throws Exception {
		balancingInterceptor.setDispatchingStrategy(roundRobinStrategy);

		HttpClient client = new HttpClient();
		Http11Test.initExpect100ContinueWithFastFail(client);

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
		PostMethod post = new PostMethod(
				"http://localhost:7000/axis2/services/BLZService");
		post.setRequestEntity(new InputStreamRequestEntity(this.getClass()
				.getResourceAsStream("/getBank.xml")));
		post.setRequestHeader(Header.CONTENT_TYPE, MimeType.TEXT_XML_UTF8);
		post.setRequestHeader(Header.SOAP_ACTION, "");

		return post;
	}

	@Test
	public void testFailOver() throws Exception {
		balancingInterceptor.setDispatchingStrategy(roundRobinStrategy);

		HttpClient client = new HttpClient();
		client.getParams().setParameter(HttpProtocolParams.PROTOCOL_VERSION,
				HttpVersion.HTTP_1_1);

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
