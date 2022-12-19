/* Copyright 2011, 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.balancer;

import static com.predic8.membrane.core.util.ByteUtil.getByteArrayData;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.rules.ServiceProxy;
import com.predic8.membrane.core.rules.ServiceProxyKey;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class ClusterBalancerTest {

	private static XMLElementSessionIdExtractor extracor;
	private static LoadBalancingInterceptor lb;
	private static Router r;

	@BeforeAll
	public static void setUp() throws Exception {

		extracor = new XMLElementSessionIdExtractor();
		extracor.setLocalName("session");
		extracor.setNamespace("http://predic8.com/session/");

		r = new HttpRouter();

		lb = new LoadBalancingInterceptor();
		lb.setSessionIdExtractor(extracor);
		lb.setName("Default");

		ServiceProxy sp = new ServiceProxy(new ServiceProxyKey(3011), "predic8.com", 80);
		sp.getInterceptors().add(lb);
		r.getRuleManager().addProxyAndOpenPortIfNew(sp);
		r.init();

		BalancerUtil.up(r, "Default", "Default", "localhost", 2000);
		BalancerUtil.up(r, "Default", "Default", "localhost", 3000);
	}

	@AfterAll
	public static void tearDown() throws Exception {
		r.shutdown();
		//let the test wait so the next test can reopen the same port and avoid PortOccupiedException
		Thread.sleep(400);
	}

	@Test
	public void testClusterBalancerRequest() throws Exception {
		Exchange exc = getExchangeWithSession();

		lb.handleRequest(exc);

		Session s = BalancerUtil.getSessions(r, "Default", "Default").get("555555");
		assertEquals("localhost", s.getNode().getHost());

		assertEquals(2, exc.getDestinations().size());

		String stickyDestination = exc.getDestinations().get(0);
		lb.handleRequest(exc);

		assertEquals(1, BalancerUtil.getSessions(r, "Default", "Default").size());
		assertEquals(stickyDestination, exc.getDestinations().get(0));

		BalancerUtil.takeout(r, "Default", "Default", "localhost", s.getNode().getPort());
		assertEquals(1, BalancerUtil.getAvailableNodesByCluster(r, "Default", "Default").size());
		assertFalse(stickyDestination.equals(BalancerUtil.getAvailableNodesByCluster(r, "Default", "Default").get(0)));

		lb.handleRequest(exc);
		assertEquals(stickyDestination, exc.getDestinations().get(0));

		BalancerUtil.down(r, "Default", "Default", "localhost", s.getNode().getPort());
		lb.handleRequest(exc);

		assertFalse(stickyDestination.equals(exc.getDestinations().get(0)));
	}

	@Test
	public void testClusterBalancerNoSessionRequestResponse() throws Exception {
		Exchange exc = getExchangeWithOutSession();

		lb.handleRequest(exc);
		assertNull(BalancerUtil.getSessions(r, "Default", "Default").get("444444"));

		Node stickyNode = (Node)exc.getProperty("dispatchedNode");
		assertNotNull(stickyNode);

		exc.setResponse(getResponse());

		lb.handleResponse(exc);

		assertEquals(stickyNode, BalancerUtil.getSessions(r, "Default", "Default").get("444444").getNode());

	}

	@Test
	public void testNoNodeFound() throws Exception {
		Exchange exc = getExchangeWithOutSession();

		BalancerUtil.down(r, "Default", "Default", "localhost", 2000);
		BalancerUtil.down(r, "Default", "Default", "localhost", 3000);

		lb.handleRequest(exc);
		assertEquals(500, exc.getResponse().getStatusCode());
	}

	private Response getResponse() throws IOException {
		Response res = Response.ok().build();
		res.setHeader(getHeader());
		res.setBodyContent(getByteArrayData(getClass().getResourceAsStream(
				"/getBankResponsewithSession.xml")));
		return res;
	}

	private Exchange getExchangeWithSession() throws IOException {
		Exchange exc = new Exchange(null);
		Request res = new Request();
		res.setHeader(getHeader());
		res.setBodyContent(getByteArrayData(getClass().getResourceAsStream(
				"/getBankwithSession555555.xml")));
		exc.setRequest(res);
		exc.setOriginalRequestUri("/axis2/services/BLZService");
		return exc;
	}

	private Exchange getExchangeWithOutSession() throws IOException {
		Exchange exc = new Exchange(null);
		Request res = new Request();
		res.setHeader(getHeader());
		res.setBodyContent(getByteArrayData(getClass().getResourceAsStream(
				"/getBank.xml")));
		exc.setRequest(res);
		exc.setOriginalRequestUri("/axis2/services/BLZService");
		return exc;
	}

	private Header getHeader() {
		Header h = new Header();
		h.setContentType("application/soap+xml");
		return h;
	}

}
