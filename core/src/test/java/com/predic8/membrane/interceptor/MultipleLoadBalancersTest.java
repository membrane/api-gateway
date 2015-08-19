/* Copyright 2012 predic8 GmbH, www.predic8.com

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

import java.io.IOException;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpVersion;
import org.apache.commons.httpclient.methods.InputStreamRequestEntity;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.http.params.HttpProtocolParams;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.MimeType;
import com.predic8.membrane.core.interceptor.balancer.BalancerUtil;
import com.predic8.membrane.core.interceptor.balancer.DispatchingStrategy;
import com.predic8.membrane.core.interceptor.balancer.LoadBalancingInterceptor;
import com.predic8.membrane.core.interceptor.balancer.RoundRobinStrategy;
import com.predic8.membrane.core.rules.ServiceProxy;
import com.predic8.membrane.core.rules.ServiceProxyKey;
import com.predic8.membrane.core.services.DummyWebServiceInterceptor;

public class MultipleLoadBalancersTest {
	private MockService service1, service2, service11, service12;

	protected LoadBalancingInterceptor balancingInterceptor1, balancingInterceptor2;
	private DispatchingStrategy roundRobinStrategy1, roundRobinStrategy2;
	protected HttpRouter balancer;

	private class MockService {
		final int port;
		final HttpRouter service1;
		final DummyWebServiceInterceptor mockInterceptor1;

		MockService(int port) throws Exception {
			this.port = port;
			service1 = new HttpRouter();
			mockInterceptor1 = new DummyWebServiceInterceptor();
			ServiceProxy sp1 = new ServiceProxy(new ServiceProxyKey("localhost",
					"POST", ".*", port), "thomas-bayer.com", 80);
			sp1.getInterceptors().add(mockInterceptor1);
			service1.getRuleManager().addProxyAndOpenPortIfNew(sp1);
			service1.init();
		}

		public void close() throws IOException {
			service1.shutdown();
		}
	}

	private LoadBalancingInterceptor createBalancingInterceptor(int port, String name) throws Exception {
		ServiceProxy sp3 = new ServiceProxy(new ServiceProxyKey("localhost", "POST", ".*", port), "thomas-bayer.com", 80);
		LoadBalancingInterceptor balancingInterceptor1 = new LoadBalancingInterceptor();
		balancingInterceptor1.setName(name);
		sp3.getInterceptors().add(balancingInterceptor1);
		balancer.getRuleManager().addProxyAndOpenPortIfNew(sp3);
		balancer.init();
		return balancingInterceptor1;
	}


	@Before
	public void setUp() throws Exception {

		service1 = new MockService(2001);
		service2 = new MockService(2002);
		service11 = new MockService(2011);
		service12 = new MockService(2012);

		balancer = new HttpRouter();

		balancingInterceptor1 = createBalancingInterceptor(7000, "Default");
		balancingInterceptor2 = createBalancingInterceptor(7001, "Balancer2");

		BalancerUtil.lookupBalancer(balancer, "Default").up("Default", "localhost", service1.port);
		BalancerUtil.lookupBalancer(balancer, "Default").up("Default", "localhost", service2.port);

		BalancerUtil.lookupBalancer(balancer, "Balancer2").up("Default", "localhost", service11.port);
		BalancerUtil.lookupBalancer(balancer, "Balancer2").up("Default", "localhost", service12.port);


		roundRobinStrategy1 = new RoundRobinStrategy();
		roundRobinStrategy2 = new RoundRobinStrategy();
	}

	@After
	public void tearDown() throws Exception {
		service1.close();
		service2.close();
		service11.close();
		service12.close();
		balancer.shutdown();
	}

	private void assertMockCounters(int n1, int n2, int n11, int n12) {
		assertEquals(n1, service1.mockInterceptor1.counter);
		assertEquals(n2, service2.mockInterceptor1.counter);
		assertEquals(n11, service11.mockInterceptor1.counter);
		assertEquals(n12, service12.mockInterceptor1.counter);
	}

	@Test
	public void testRoundRobinDispachingStrategy() throws Exception {
		balancingInterceptor1.setDispatchingStrategy(roundRobinStrategy1);
		balancingInterceptor2.setDispatchingStrategy(roundRobinStrategy2);

		HttpClient client = new HttpClient();
		client.getParams().setParameter(HttpProtocolParams.PROTOCOL_VERSION,
				HttpVersion.HTTP_1_1);

		PostMethod vari = getPostMethod(7000);
		int status = client.executeMethod(vari);

		assertEquals(200, status);
		assertMockCounters(1, 0, 0, 0);

		assertEquals(200, client.executeMethod(getPostMethod(7000)));
		assertMockCounters(1, 1, 0, 0);

		assertEquals(200, client.executeMethod(getPostMethod(7000)));
		assertMockCounters(2, 1, 0, 0);

		assertEquals(200, client.executeMethod(getPostMethod(7000)));
		assertMockCounters(2, 2, 0, 0);

		assertEquals(200, client.executeMethod(getPostMethod(7001)));
		assertMockCounters(2, 2, 1, 0);

		assertEquals(200, client.executeMethod(getPostMethod(7001)));
		assertMockCounters(2, 2, 1, 1);

		assertEquals(200, client.executeMethod(getPostMethod(7001)));
		assertMockCounters(2, 2, 2, 1);
	}

	private PostMethod getPostMethod(int port) {
		PostMethod post = new PostMethod(
				"http://localhost:" + port + "/axis2/services/BLZService");
		post.setRequestEntity(new InputStreamRequestEntity(this.getClass()
				.getResourceAsStream("/getBank.xml")));
		post.setRequestHeader(Header.CONTENT_TYPE, MimeType.TEXT_XML_UTF8);
		post.setRequestHeader(Header.SOAP_ACTION, "");

		return post;
	}

}
