/* Copyright 2015 predic8 GmbH, www.predic8.com

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

import org.junit.jupiter.api.Test;

import com.predic8.membrane.core.exchange.Exchange;

import static com.predic8.membrane.core.interceptor.balancer.Node.Status.DOWN;
import static com.predic8.membrane.core.interceptor.balancer.Node.Status.UP;
import static org.junit.jupiter.api.Assertions.*;

public class NodeOnlineCheckerTest {

	@Test
	void exchangeWithException()
	{
		Node node = new Node("http://www.predic8.de",80);

		Exchange exc = new Exchange(null);
		exc.getDestinations().addFirst("http://www.predic8.de:80");
		exc.trackNodeException(0, new Exception());

		LoadBalancingInterceptor lbi = new LoadBalancingInterceptor();
		Cluster cl = lbi.getClusterManager().getClusters().getFirst();
		cl.nodeUp(node);
		assertEquals(UP, cl.getNode(node).getStatus());

		NodeOnlineChecker noc = new NodeOnlineChecker();
		lbi.setNodeOnlineChecker(noc);

		noc.handle(exc);
		assertEquals(DOWN,cl.getNode(node).getStatus());
	}

	@Test
	void exchangeWithBadStatuscode(){
		Node node = new Node("http://www.predic8.de",80);

		Exchange exc = new Exchange(null);
		exc.getDestinations().addFirst("http://www.predic8.de:80");
		exc.setNodeStatusCode(0,500);

		LoadBalancingInterceptor lbi = new LoadBalancingInterceptor();
		Cluster cl = lbi.getClusterManager().getClusters().getFirst();
		cl.nodeUp(node);
		assertEquals(UP, cl.getNode(node).getStatus());

		NodeOnlineChecker noc = new NodeOnlineChecker();
		lbi.setNodeOnlineChecker(noc);

		final int limit = 10;
		noc.setNodeCounterLimit5XX(limit);



		for(int i = 0; i < limit; i++){
			noc.handle(exc);
		}
		assertEquals(UP, cl.getNode(node).getStatus());
		exc.setNodeStatusCode(0,400);
		noc.handle(exc);
		assertEquals(UP, cl.getNode(node).getStatus());
		exc.setNodeStatusCode(0,500);
		for(int i = 0; i < limit+1;i++){
			noc.handle(exc);
		}
		assertEquals(DOWN, cl.getNode(node).getStatus());

	}

	@Test
	public void testPutNodesBackOnline() throws InterruptedException {
		Node node = new Node("http://www.predic8.de",80);

		Exchange exc = new Exchange(null);
		exc.getDestinations().addFirst("http://www.predic8.de:80");
		exc.setNodeStatusCode(0,500);

		LoadBalancingInterceptor lbi = new LoadBalancingInterceptor();
		Cluster cl = lbi.getClusterManager().getClusters().getFirst();
		cl.nodeUp(node);
		assertEquals(UP, cl.getNode(node).getStatus());

		NodeOnlineChecker noc = new NodeOnlineChecker();
		lbi.setNodeOnlineChecker(noc);

		final int limit = 10;
		noc.setNodeCounterLimit5XX(limit);

		noc.setRetryTimeInSeconds(1);

		assertEquals(UP, cl.getNode(node).getStatus());
		for(int i = 0; i < limit + 1; i++){
			noc.handle(exc);
		}
		assertEquals(DOWN, cl.getNode(node).getStatus());
		noc.putNodesBackUp();
		assertEquals(DOWN, cl.getNode(node).getStatus());
		Thread.sleep(noc.getRetryTimeInSeconds()*1000);
		noc.putNodesBackUp();
		assertEquals(UP, cl.getNode(node).getStatus());
	}
}
