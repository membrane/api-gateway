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

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.balancer.*;
import com.predic8.membrane.core.proxies.*;
import com.predic8.membrane.core.services.*;
import com.predic8.membrane.core.util.*;
import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.methods.*;
import org.junit.jupiter.api.*;

import java.net.*;
import java.util.*;

import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.util.NetworkUtil.*;
import static java.util.Objects.*;
import static org.apache.http.params.HttpProtocolParams.*;
import static org.junit.jupiter.api.Assertions.*;

public class LoadBalancingInterceptorTest {

	private DummyWebServiceInterceptor mockInterceptor1;
	private DummyWebServiceInterceptor mockInterceptor2;
	protected LoadBalancingInterceptor balancingInterceptor;
	private DispatchingStrategy roundRobinStrategy;
	private DispatchingStrategy byThreadStrategy;
	private DispatchingStrategy priorityStrategy;
	private HttpRouter service1;
	private HttpRouter service2;
	protected HttpRouter balancer;

    @BeforeEach
	public void setUp() throws Exception {
        int port2k = getFreePortEqualAbove(2000);
        int port3k = getFreePortEqualAbove(3000);
		service1 = new HttpRouter();
		mockInterceptor1 = new DummyWebServiceInterceptor();
		ServiceProxy sp1 = new ServiceProxy(new ServiceProxyKey("localhost",
				"POST", ".*", port2k), "dummy", 80);
		sp1.getInterceptors().add(new AbstractInterceptor(){
			@Override
			public Outcome handleResponse(Exchange exc) {
				exc.getResponse().getHeader().add("Connection", "close");
				return CONTINUE;
			}
		});
		sp1.getInterceptors().add(mockInterceptor1);
		service1.getRuleManager().addProxyAndOpenPortIfNew(sp1);
		service1.init();

		service2 = new HttpRouter();
		mockInterceptor2 = new DummyWebServiceInterceptor();
		ServiceProxy sp2 = new ServiceProxy(new ServiceProxyKey("localhost",
				"POST", ".*", port3k), "dummy", 80);
		sp2.getInterceptors().add(new AbstractInterceptor(){
			@Override
			public Outcome handleResponse(Exchange exc) {
				exc.getResponse().getHeader().add("Connection", "close");
				return CONTINUE;
			}
		});
		sp2.getInterceptors().add(mockInterceptor2);
		service2.getRuleManager().addProxyAndOpenPortIfNew(sp2);
		service2.init();

		balancer = new HttpRouter();
		ServiceProxy sp3 = new ServiceProxy(new ServiceProxyKey("localhost",
				"POST", ".*", 3054), "dummy", 80);
		balancingInterceptor = new LoadBalancingInterceptor();
		balancingInterceptor.setName("Default");
		sp3.getInterceptors().add(balancingInterceptor);
		balancer.getRuleManager().addProxyAndOpenPortIfNew(sp3);
		enableFailOverOn5XX(balancer);
		balancer.init();

		BalancerUtil.lookupBalancer(balancer, "Default").up("Default", "localhost", port2k);
		BalancerUtil.lookupBalancer(balancer, "Default").up("Default", "localhost", port3k);

		roundRobinStrategy = new RoundRobinStrategy();
		byThreadStrategy = new ByThreadStrategy();
		priorityStrategy = new PriorityStrategy();
	}

	private void enableFailOverOn5XX(HttpRouter balancer2) {
		List<Interceptor> l = balancer.getTransport().getInterceptors();
		((HTTPClientInterceptor)l.getLast()).setFailOverOn5XX(true);
	}

	@AfterEach
	void tearDown() {
		service1.shutdown();
		service2.shutdown();
		balancer.shutdown();
	}

	@Test
	void testGetDestinationURLWithHostname() throws URISyntaxException {
		doTestGetDestinationURL(
				"http://localhost/axis2/services/BLZService?wsdl",
				"http://thomas-bayer.com:80/axis2/services/BLZService?wsdl");
	}

	@Test
	void testGetDestinationURLWithoutHostname()
			throws URISyntaxException {
		doTestGetDestinationURL("/axis2/services/BLZService?wsdl",
				"http://thomas-bayer.com:80/axis2/services/BLZService?wsdl");
	}

	private void doTestGetDestinationURL(String requestUri, String expectedUri) throws URISyntaxException {
		Exchange exc = new Exchange(null);
		exc.setRequest(new Request());
		exc.getRequest().setUri(URLUtil.getPathQuery(new URIFactory(), requestUri));
		exc.setOriginalRequestUri(requestUri);
		assertEquals(expectedUri, new Node("thomas-bayer.com", 80).getDestinationURL(exc));
	}

	@Test
	void testRoundRobinDispatchingStrategy() throws Exception {
		balancingInterceptor.setDispatchingStrategy(roundRobinStrategy);

		HttpClient client = new HttpClient();
		client.getParams().setParameter(PROTOCOL_VERSION, HttpVersion.HTTP_1_1);

		PostMethod vari = getPostMethod();
		int status = client.executeMethod(vari);
		//System.out.println(new String(vari.getResponseBody()));

		assertEquals(200, status);
		assertEquals(1, mockInterceptor1.getCount());
		assertEquals(0, mockInterceptor2.getCount());

		assertEquals(200, client.executeMethod(getPostMethod()));
		assertEquals(1, mockInterceptor1.getCount());
		assertEquals(1, mockInterceptor2.getCount());

		assertEquals(200, client.executeMethod(getPostMethod()));
		assertEquals(2, mockInterceptor1.getCount());
		assertEquals(1, mockInterceptor2.getCount());

		assertEquals(200, client.executeMethod(getPostMethod()));
		assertEquals(2, mockInterceptor1.getCount());
		assertEquals(2, mockInterceptor2.getCount());
	}

	@Test
	void testPriorityStrategyDispatching() {
		balancingInterceptor.setDispatchingStrategy(priorityStrategy);
	}

	@Test
	void testExpect100Continue() throws Exception {
		balancingInterceptor.setDispatchingStrategy(roundRobinStrategy);

		HttpClient client = new HttpClient();
		Http11Test.initExpect100ContinueWithFastFail(client);

		PostMethod vari = getPostMethod();
		int status = client.executeMethod(vari);

		assertEquals(200, status);
		assertEquals(1, mockInterceptor1.getCount());
		assertEquals(0, mockInterceptor2.getCount());

		assertEquals(200, client.executeMethod(getPostMethod()));
		assertEquals(1, mockInterceptor1.getCount());
		assertEquals(1, mockInterceptor2.getCount());

		assertEquals(200, client.executeMethod(getPostMethod()));
		assertEquals(2, mockInterceptor1.getCount());
		assertEquals(1, mockInterceptor2.getCount());

		assertEquals(200, client.executeMethod(getPostMethod()));
		assertEquals(2, mockInterceptor1.getCount());
		assertEquals(2, mockInterceptor2.getCount());
	}

	private PostMethod getPostMethod() {
		PostMethod post = new PostMethod(
				"http://localhost:3054/axis2/services/BLZService");
		post.setRequestEntity(new InputStreamRequestEntity(requireNonNull(this.getClass()
				.getResourceAsStream("/getBank.xml"))));
		post.setRequestHeader(CONTENT_TYPE, TEXT_XML_UTF8);
		post.setRequestHeader(SOAP_ACTION, "");

		return post;
	}

	@Test
	void testFailOverOnConnectionRefused() throws Exception {
		balancingInterceptor.setDispatchingStrategy(roundRobinStrategy);

		HttpClient client = new HttpClient();
		client.getParams().setParameter(PROTOCOL_VERSION,
				HttpVersion.HTTP_1_1);

		assertEquals(200, client.executeMethod(getPostMethod()));
		assertEquals(1, mockInterceptor1.getCount());
		assertEquals(0, mockInterceptor2.getCount());

		assertEquals(200, client.executeMethod(getPostMethod()));
		assertEquals(1, mockInterceptor1.getCount());
		assertEquals(1, mockInterceptor2.getCount());

		service1.shutdown();
		Thread.sleep(1000);

		assertEquals(200, client.executeMethod(getPostMethod()));
		assertEquals(1, mockInterceptor1.getCount());
		assertEquals(2, mockInterceptor2.getCount());

		assertEquals(200, client.executeMethod(getPostMethod()));
		assertEquals(3, mockInterceptor2.getCount());

	}

	@Test
	void testFailOverOnStatus500() throws Exception {
		balancingInterceptor.setDispatchingStrategy(roundRobinStrategy);

		HttpClient client = new HttpClient();
		client.getParams().setParameter(PROTOCOL_VERSION,
				HttpVersion.HTTP_1_1);

		assertEquals(200, client.executeMethod(getPostMethod()));
		assertEquals(1, mockInterceptor1.getCount());
		assertEquals(0, mockInterceptor2.getCount());

		assertEquals(200, client.executeMethod(getPostMethod()));
		assertEquals(1, mockInterceptor1.getCount());
		assertEquals(1, mockInterceptor2.getCount());

		service1.getRuleManager().getRules().getFirst().getInterceptors().addFirst(new AbstractInterceptor(){
			@Override
			public Outcome handleRequest(Exchange exc) {
				exc.setResponse(Response.internalServerError().build());
				return ABORT;
			}
		});

		assertEquals(200, client.executeMethod(getPostMethod()));
		assertEquals(1, mockInterceptor1.getCount());
		assertEquals(2, mockInterceptor2.getCount());

		assertEquals(200, client.executeMethod(getPostMethod()));
		assertEquals(3, mockInterceptor2.getCount());

	}

	@Test
	void testByThreadStrategy() {
		balancingInterceptor.setDispatchingStrategy(byThreadStrategy);
	}
}
