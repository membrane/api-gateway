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

package com.predic8.membrane.core.util;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DNSCacheTest {
    
    private static final Logger LOG = LoggerFactory.getLogger(DNSCacheTest.class);

	private DNSCache cache = new DNSCache();
	
	private static InetAddress address;
	
	static {
		try {
			address = InetAddress.getByName("localhost");
		} catch (UnknownHostException e) {
		    LOG.warn(e.getMessage(), e);
		}
	}
	
	@Test
	public void testGetHostName() throws Exception {
		String host = cache.getHostName(address);
		assertEquals("localhost", host);
		assertTrue(cache.getCachedHostNames().contains(host));
	}
	
	@Test
	public void testGetHostAddress() throws Exception {
		String host = cache.getHostAddress(address);
		assertEquals("127.0.0.1", host);
		assertTrue(cache.getCachedHostAddresses().contains(host));
	}
	
}
