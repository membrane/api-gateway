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

import java.net.InetAddress;
import java.util.Collection;
import java.util.Hashtable;
import java.util.Map;

/**
 * See InetAddress Caching of InetAddress class
 * 
 * Java 1.5 implementation of InetAddress Caching differs from Java 1.6 one.  
 * 
 * 
 * @author predic8
 *
 */
public class DNSCache {

	private Map<InetAddress, String> hostNames = new Hashtable<InetAddress, String>();
	
	private Map<InetAddress, String> hostAddresses = new Hashtable<InetAddress, String>();
	
	public String getHostName(InetAddress address) {
		if (hostNames.containsKey(address))
			return hostNames.get(address);
		
		String hostName = address.getHostName();
		hostNames.put(address, hostName);
		return hostName;
	}
	
	public String getHostAddress(InetAddress address) {
		if (hostAddresses.containsKey(address))
			return hostAddresses.get(address);
		
		String hostAddress = address.getHostAddress();
		hostAddresses.put(address, hostAddress);
		return hostAddress;
	}
	
	public Collection<String> getCachedHostNames() {
		return hostNames.values();
	}
	
	public Collection<String> getCachedHostAddresses() {
		return hostAddresses.values();
	}
	
}
