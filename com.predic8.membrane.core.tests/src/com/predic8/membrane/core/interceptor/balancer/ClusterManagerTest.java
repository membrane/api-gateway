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

import junit.framework.TestCase;

import org.junit.Test;


public class ClusterManagerTest extends TestCase {
	
	private ClusterManager cm = new ClusterManager();
	
	@Test
	public void testAddEndpoint() throws Exception {

		cm.up("c1", "localhost", 2000);
		
		assertEquals(1, cm.getAllNodes("c1").size());
		assertEquals(true, cm.getAllNodes("c1").get(0).isUp());
		assertEquals("localhost", cm.getAllNodes("c1").get(0).getHost());
		assertEquals(2000, cm.getAllNodes("c1").get(0).getPort());
	}	

	@Test
	public void testDownEndpoint() throws Exception {

		cm.down("c2", "localhost", 2000);
		assertEquals(false, cm.getAllNodes("c2").get(0).isUp());
	}	

	@Test
	public void testEmptyEndpoints() throws Exception {

		assertEquals(0, cm.getAllNodes("c3").size());
	}	
	
	@Test
	public void testTimeout() throws Exception {

		cm.up("c3", "localhost", 2000);
		cm.setTimeout(2000);
		assertEquals(1, cm.getAvailableNodes("c3").size());
		Thread.sleep(3000);
		assertEquals(0, cm.getAvailableNodes("c3").size());
	}	
	
}
