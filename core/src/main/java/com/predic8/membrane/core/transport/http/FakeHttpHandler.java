/* Copyright 2012, 2024 predic8 GmbH, www.predic8.com

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

public class FakeHttpHandler extends AbstractHttpHandler {

	private final int port;
	private InetAddress address;

	public FakeHttpHandler(int port) {
		super(null);
		this.port = port;
	}

	public FakeHttpHandler(int port, InetAddress addr) {
		super(null);
		this.port = port;
		this.address = addr;
	}

	@Override
	public void shutdownInput() {
	}

	@Override
	public InetAddress getLocalAddress() {
		return address;
	}

	@Override
	public int getLocalPort() {
		return port;
	}

}
