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

public record HostColonPort(boolean useSSL, String host, int port) {

	public HostColonPort(boolean useSSL, String hostAndPort) {
		this(useSSL, hostPart(hostAndPort), portPart(hostAndPort,  useSSL ? 443 : 80));
	}

	public HostColonPort(String host, int port) {
		this(false, host, port);
	}

	public HostColonPort(URL url) throws MalformedURLException {
		this(url.getProtocol().endsWith("s"), url.getHost(), HttpUtil.getPort(url));
	}

	public String getProtocol() {
		// TODO shouldn't this check useSSL instead?
		return (isHttpsPort() ? "https" : "http");
	}

	public String getUrl() {
		return "%s://%s".formatted(getProtocol(), this);
	}

	@Override
	public String toString() {
		return host + ":" + port;
	}

	private boolean isHttpsPort() {
		return port == 443 || port == 8443;
	}

	private static String hostPart(String addr) {
		var colon = addr.indexOf(":");
		return (colon > -1 ? addr.substring(0, colon) : addr);
	}

	private static int portPart(String addr, int defaultValue) {
		var colon = addr.indexOf(":");
		return (colon > -1 ? Integer.parseInt(addr.substring(colon + 1)) : defaultValue);
	}
}
