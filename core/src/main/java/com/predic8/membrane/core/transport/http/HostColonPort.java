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
package com.predic8.membrane.core.transport.http;

import java.net.MalformedURLException;
import java.net.URL;

import com.predic8.membrane.core.util.HttpUtil;

public class HostColonPort {

	public final boolean useSSL;
	public final String host;
	public final int port;
	
	public HostColonPort(boolean useSSL, String hostAndPort) {
		String[] strs = hostAndPort.split(":");
		
		this.useSSL = useSSL;
		host = strs[0];
		port = strs.length > 1 ? Integer.parseInt(strs[1]) : 80;
	}
	
	public HostColonPort(boolean useSSL, String host, int port) {
		this.useSSL = useSSL;
		this.host = host;
		this.port = port;
	}
	
	public HostColonPort(URL url) throws MalformedURLException {
		useSSL = url.getProtocol().endsWith("s");
		host = url.getHost();
		port = HttpUtil.getPort(url);
	}
	
	@Override
	public String toString() {
		return host + ":" + port;
	}
}
