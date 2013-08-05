/* Copyright 2009, 2012 predic8 GmbH, www.predic8.com

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

import static org.junit.Assert.assertEquals;

import java.net.MalformedURLException;
import java.util.List;

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
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.HTTPClientInterceptor;
import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.balancer.BalancerUtil;
import com.predic8.membrane.core.interceptor.balancer.ByThreadStrategy;
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
		sp1.getInterceptors().add(new AbstractInterceptor(){
			@Override
			public Outcome handleResponse(Exchange exc) throws Exception {
				exc.getResponse().getHeader().add("Connection", "close");
				return Outcome.CONTINUE;
			}
		});
		sp1.getInterceptors().add(mockInterceptor1);
		service1.getRuleManager().addProxyAndOpenPortIfNew(sp1);
		service1.init();

		service2 = new HttpRouter();
		mockInterceptor2 = new DummyWebServiceInterceptor();
		ServiceProxy sp2 = new ServiceProxy(new ServiceProxyKey("localhost",
				"POST", ".*", 3000), "thomas-bayer.com", 80);
		sp2.getInterceptors().add(new AbstractInterceptor(){
			@Override
			public Outcome handleResponse(Exchange exc) throws Exception {
				exc.getResponse().getHeader().add("Connection", "close");
				return Outcome.CONTINUE;
			}
		});
		sp2.getInterceptors().add(mockInterceptor2);
		service2.getRuleManager().addProxyAndOpenPortIfNew(sp2);
		service2.init();

		balancer = new HttpRouter();
		ServiceProxy sp3 = new ServiceProxy(new ServiceProxyKey("localhost",
				"POST", ".*", 7000), "thomas-bayer.com", 80);
		balancingInterceptor = new LoadBalancingInterceptor();
		balancingInterceptor.setName("Default");
		sp3.getInterceptors().add(balancingInterceptor);
		balancer.getRuleManager().addProxyAndOpenPortIfNew(sp3);
		enableFailOverOn5XX(balancer);
		balancer.init();

		BalancerUtil.lookupBalancer(balancer, "Default").up("Default", "localhost", 2000);
		BalancerUtil.lookupBalancer(balancer, "Default").up("Default", "localhost", 3000);

		roundRobinStrategy = new RoundRobinStrategy();
		byThreadStrategy = new ByThreadStrategy();
	}

	private void enableFailOverOn5XX(HttpRouter balancer2) {
		List<Interceptor> l = balancer.getTransport().getInterceptors();
		((HTTPClientInterceptor)l.get(l.size()-1)).setFailOverOn5XX(true);
	}

	@After
	public void tearDown() throws Exception {
		service1.shutdown();
		service2.shutdown();
		balancer.shutdown();
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

	private void doTestGetDestinationURL(String requestUri, String expectedUri) {
		Exchange exc = new Exchange(null);
		exc.setOriginalRequestUri(requestUri);
		assertEquals(expectedUri, new Node("thomas-bayer.com", 80).getDestinationURL(exc));
	}

	@Test
	public void testRoundRobinDispachingStrategy() throws Exception {
		balancingInterceptor.setDispatchingStrategy(roundRobinStrategy);

		HttpClient client = new HttpClient();
		client.getParams().setParameter(HttpProtocolParams.PROTOCOL_VERSION,
				HttpVersion.HTTP_1_1);

		PostMethod vari = getPostMethod();
		int status = client.executeMethod(vari);
		//System.out.println(new String(vari.getResponseBody()));

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
	public void testFailOverOnConnectionRefused() throws Exception {
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

		service1.shutdown();
		Thread.sleep(1000);

		assertEquals(200, client.executeMethod(getPostMethod()));
		assertEquals(1, mockInterceptor1.counter);
		assertEquals(2, mockInterceptor2.counter);

		assertEquals(200, client.executeMethod(getPostMethod()));
		assertEquals(3, mockInterceptor2.counter);

	}

	@Test
	public void testFailOverOnStatus500() throws Exception {
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

		((ServiceProxy)service1.getRuleManager().getRules().get(0)).getInterceptors().add(0, new AbstractInterceptor(){
			@Override
			public Outcome handleRequest(Exchange exc) throws Exception {
				exc.setResponse(Response.interalServerError().build());
				return Outcome.ABORT;
			}
		});

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
