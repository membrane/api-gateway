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

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;

import java.util.Objects;

@MCElement(name="connection", topLevel=false)
public class ConnectionConfiguration {

	private long keepAliveTimeout = 4000;
	private int connectTimeout = 10000;
	private int soTimeout = 0;
	private String localAddr;

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		ConnectionConfiguration that = (ConnectionConfiguration) o;
		return keepAliveTimeout == that.keepAliveTimeout
				&& connectTimeout == that.connectTimeout
				&& soTimeout == that.soTimeout
				&& Objects.equals(localAddr, that.localAddr);
	}

	@Override
	public int hashCode() {
		return Objects.hash(keepAliveTimeout, connectTimeout, soTimeout, localAddr);
	}

	public long getKeepAliveTimeout() {
		return keepAliveTimeout;
	}

	/**
	 * @description Time in milliseconds after which an open connection to the server is not reused. Be sure to set it to a smaller value than the KeepAlive
	 * directive on your server. Note that the a "Keep-Alive" header in the response always takes precedence.
	 * @default 4000
	 * @example 30000
	 */
	@MCAttribute
	public void setKeepAliveTimeout(long keepAliveTimeout) {
		this.keepAliveTimeout = keepAliveTimeout;
	}

	public int getTimeout() {
		return connectTimeout;
	}

	/**
	 * @description Socket timeout (connect) in milliseconds.
	 * @default 10000
	 */
	@MCAttribute
	public void setTimeout(int timeout) {
		this.connectTimeout = timeout;
	}

	public int getSoTimeout() {
		return soTimeout;
	}

	/**
	 * @description Socket timeout (read, etc.) in milliseconds. A value of 0 means 'unlimited'.
	 * @default 0
	 */
	@MCAttribute
	public void setSoTimeout(int soTimeout) {
		this.soTimeout = soTimeout;
	}

	public String getLocalAddr() {
		return localAddr;
	}

	/**
	 * @description The local IP address to use for outbound connections.
	 * @default not set
	 */
	@MCAttribute
	public void setLocalAddr(String localAddr) {
		this.localAddr = localAddr;
	}
}
