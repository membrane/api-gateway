/* Copyright 2013 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.transport.http.client;




public class ConnectionConfiguration {

	private long keepAliveTimeout = 4000;
	private int connectTimeout = 10000;
	private String localAddr;

	public long getKeepAliveTimeout() {
		return keepAliveTimeout;
	}

	/**
	 * @description Time in milliseconds after which an open connection to the server is not reused. Be sure to set it to a smaller value than the KeepAlive
					directive on your server. Note that the a "Keep-Alive" header in the response always takes precedence.
	 * @default 4000
	 * @example 30000
	 */
	
	public void setKeepAliveTimeout(long keepAliveTimeout) {
		this.keepAliveTimeout = keepAliveTimeout;
	}

	public int getTimeout() {
		return connectTimeout;
	}

	/**
	 * @description Socket timeout (connect, read, etc.) in milliseconds.
	 * @default 10000
	 */
	
	public void setTimeout(int timeout) {
		this.connectTimeout = timeout;
	}

	public String getLocalAddr() {
		return localAddr;
	}

	/**
	 * @description The local IP address to use for outbound connections.
	 * @default not set
	 */
	
	public void setLocalAddr(String localAddr) {
		this.localAddr = localAddr;
	}


}
