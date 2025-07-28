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

/**
 * @description Configuration for low-level socket behavior of Membrane's HTTP client.
 *              This includes timeouts and local address binding for outbound connections.
 *              Typically used as a child of &lt;httpClientConfig&gt;.
 *
 *              XML Example:
 *              &lt;connection keepAliveTimeout="30000" timeout="10000" soTimeout="5000" localAddr="192.168.1.100"/&gt;
 *
 *              YAML (experimental):
 *              connection:
 *                keepAliveTimeout: 30000
 *                timeout: 10000
 *                soTimeout: 5000
 *                localAddr: "192.168.1.100"
 *
 * @topic 4. Transports and Clients
 */
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
	 * @description Time in milliseconds before an idle connection is closed and removed from the connection pool.
	 *              Should be less than the server-side keep-alive timeout.
	 *              If a response includes a "Keep-Alive" header, that value overrides this setting.
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
	 * @description Maximum time in milliseconds to wait when establishing a TCP connection.
	 *              A value of 0 may block indefinitely depending on the system.
	 * @default 10000
	 * @example 5000
	 */
	@MCAttribute
	public void setTimeout(int timeout) {
		this.connectTimeout = timeout;
	}

	public int getSoTimeout() {
		return soTimeout;
	}

	/**
	 * @description Read timeout in milliseconds.
	 *              Applies to reading from the socket after the connection is established.
	 *              A value of 0 means infinite timeout.
	 * @default 0
	 * @example 10000
	 */
	@MCAttribute
	public void setSoTimeout(int soTimeout) {
		this.soTimeout = soTimeout;
	}

	public String getLocalAddr() {
		return localAddr;
	}

	/**
	 * @description IP address of the local network interface to use for outbound connections.
	 *              Useful in multi-homed environments.
	 * @default (not set)
	 * @example 192.168.1.42
	 */
	@MCAttribute
	public void setLocalAddr(String localAddr) {
		this.localAddr = localAddr;
	}
}
