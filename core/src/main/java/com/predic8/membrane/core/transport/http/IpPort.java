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

package com.predic8.membrane.core.transport.http;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class IpPort {
	public final InetAddress ip;
	public final int port;

	public IpPort(String ip, int port) throws UnknownHostException {
		this.ip = (ip == null) ? null : InetAddress.getByName(ip);
		this.port = port;
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof IpPort))
			return false;
		IpPort other = (IpPort)obj;
		if (other.port != port)
			return false;
		if (ip == null)
			return other.ip == null;
		return ip.equals(other.ip);
	}

	@Override
	public int hashCode() {
		return 5 * port + (ip != null ? 3 * ip.hashCode() : 0);
	}

	@Override
	public String toString() {
		return "port=" + port + " ip=" + ip;
	}


	public String toShortString() {
	    return "'" + ((ip == null) ? "*" : ip.toString()) +
                ':' + port + '\'';
	}

	/**
	 * @return the ip
	 */
	public InetAddress getIp() {
		return ip;
	}

	/**
	 * @return the port
	 */
	public int getPort() {
		return port;
	}

}
