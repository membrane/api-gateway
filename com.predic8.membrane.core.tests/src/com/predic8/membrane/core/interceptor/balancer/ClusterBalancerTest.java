/* Copyright 2011 predic8 GmbH, www.predic8.com

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

import java.io.IOException;

import junit.framework.TestCase;

import org.junit.*;

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.*;

public class ClusterBalancerTest extends TestCase {

	private XMLElementSessionIdExtractor extracor;
	private ClusterManager cm;
	private LoadBalancingInterceptor lb;

	@Before
	public void setUp() throws Exception {
		
		extracor = new XMLElementSessionIdExtractor();
		extracor.setLocalName("session");
		extracor.setNamespace("http://predic8.com/session/");

		lb = new LoadBalancingInterceptor();
		lb.setSessionIdExtractor(extracor);

		cm = new ClusterManager();
		cm.up("Default", "localhost", 2000);
		cm.up("Default", "localhost", 3000);
		Router r = new HttpRouter();
		r.setClusterManager(cm);
		lb.setRouter(r);
	}

	@Test
	public void testClusterBalancerRequest() throws Exception {
		Exchange exc = getExchangeWithSession();

		lb.handleRequest(exc);
		System.out.println("1");

		Session s = cm.getSessions("Default").get("555555");
		assertEquals("localhost", s.getNode().getHost());

		assertEquals(2, exc.getDestinations().size()); 

		String stickyDestination = exc.getDestinations().get(0);
		lb.handleRequest(exc);

		assertEquals(1, cm.getSessions("Default").size());
		assertEquals(stickyDestination, exc.getDestinations().get(0));

		cm.takeout("Default", "localhost", s.getNode().getPort());
		assertEquals(1, cm.getAvailableNodesByCluster("Default").size());
		assertFalse(stickyDestination.equals(cm.getAvailableNodesByCluster("Default").get(0)));
		
		lb.handleRequest(exc);
		assertEquals(stickyDestination, exc.getDestinations().get(0));
		
		cm.down("Default", "localhost", s.getNode().getPort());
		lb.handleRequest(exc);
		
		assertFalse(stickyDestination.equals(exc.getDestinations().get(0)));		
	}

	@Test
	public void testClusterBalancerNoSessionRequestResponse() throws Exception {
		Exchange exc = getExchangeWithOutSession();

		lb.handleRequest(exc);
		assertNull(cm.getSessions("Default").get("444444"));

		Node stickyNode = (Node)exc.getProperty("dispatchedNode");
		assertNotNull(stickyNode);
		
		exc.setResponse(getResponse());

		lb.handleResponse(exc);

		assertEquals(stickyNode, cm.getSessions("Default").get("444444").getNode());

	}
	
	@Test
	public void testNoNodeFound() throws Exception {
		Exchange exc = getExchangeWithOutSession();

		cm.down("Default", "localhost", 2000);
		cm.down("Default", "localhost", 3000);

		lb.handleRequest(exc);	
		assertEquals(500, exc.getResponse().getStatusCode());
	}

	private Response getResponse() throws IOException {
		Response res = new Response();
		res.setHeader(getHeader());
		res.setBodyContent(getByteArrayData(getClass().getResourceAsStream(
				"/getBankResponsewithSession.xml")));
		return res;
	}

	private Exchange getExchangeWithSession() throws IOException {
		Exchange exc = new Exchange();
		Request res = new Request();
		res.setHeader(getHeader());
		res.setBodyContent(getByteArrayData(getClass().getResourceAsStream(
				"/getBankwithSession555555.xml")));
		exc.setRequest(res);
		exc.setOriginalRequestUri("/axis2/services/BLZService");
		return exc;
	}

	private Exchange getExchangeWithOutSession() throws IOException {
		Exchange exc = new Exchange();
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
