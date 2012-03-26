/* Copyright 2010, 2011 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor;

import static junit.framework.Assert.*;

import org.junit.Test;

import com.predic8.membrane.core.exchange.Exchange;
public class ThrottleInterceptorTest {
		
	boolean success;
	
	@Test
	public void testProtocolSet() throws Exception {
		final ThrottleInterceptor i = new ThrottleInterceptor();
		final Exchange exc = new Exchange(null);
		
		long t = System.currentTimeMillis();
		i.handleRequest(exc);
		assertTrue(System.currentTimeMillis()-t < 2000);
		
		t = System.currentTimeMillis();		
		i.setDelay(3000);
		i.handleRequest(exc);
		assertTrue(System.currentTimeMillis()-t > 2000);
		
		i.setDelay(0);
		
		i.setMaxThreads(3);
		assertEquals(Outcome.CONTINUE, i.handleRequest(exc));
				
		assertEquals(Outcome.ABORT, i.handleRequest(exc));
		assertEquals(503, exc.getResponse().getStatusCode());
		
		i.handleResponse(exc);
		assertEquals(Outcome.CONTINUE, i.handleRequest(exc));
		
		i.setBusyDelay(3000);
		t = System.currentTimeMillis();		
		assertEquals(Outcome.ABORT, i.handleRequest(exc));
		assertTrue(System.currentTimeMillis()-t > 2000);
		
		Thread thread1 = new Thread() {
			public void run() {
				try {
					success = (Outcome.CONTINUE == i.handleRequest(exc));
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			};
		};
		
		thread1.start();
		
		Thread.sleep(1000);
		i.handleResponse(exc);
		
		thread1.join();		
		
		assertTrue(success);
	}
	
}
