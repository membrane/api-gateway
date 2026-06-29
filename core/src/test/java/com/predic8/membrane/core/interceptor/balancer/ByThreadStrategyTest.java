/* Copyright 2026 predic8 GmbH, www.predic8.com

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

import com.predic8.membrane.core.exchange.Exchange;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;

public class ByThreadStrategyTest {

	/**
	 * done() must decrement the same host:port counter that dispatch() incremented, otherwise the node stays at
	 * capacity forever and dispatch() eventually fails with "All available servers are busy."
	 */
	@Test
	void releasesSlotOnDoneSoNodeIsReusable() {
		var node = new Node("localhost", 2000);
		var cluster = new Cluster();
		cluster.setNodes(List.of(node));

		var lb = new LoadBalancingInterceptor();
		lb.setClusters(List.of(cluster));

		var strategy = new ByThreadStrategy();
		strategy.setMaxNumberOfThreadsPerEndpoint(1);
		strategy.setRetryTimeOnBusy(1);

		// take the node's only slot
		var first = new Exchange(null);
		assertSame(node, strategy.dispatch(lb, first));

		// the balancer records the dispatched node and sets the original request URI; then the request completes
		first.setProperty("dispatchedNode", node);
		first.setOriginalRequestUri("http://localhost:2000/");
		strategy.done(first);

		// the slot must be free again
		assertSame(node, strategy.dispatch(lb, new Exchange(null)));
	}
}
