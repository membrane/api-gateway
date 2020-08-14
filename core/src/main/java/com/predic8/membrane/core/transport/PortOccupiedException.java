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
package com.predic8.membrane.core.transport;

import java.net.BindException;

public class PortOccupiedException extends BindException {

	private int port;

	private static final long serialVersionUID = 7778568148191933733L;


	public PortOccupiedException(int port) {
		this.port = port;
	}

	public int getPort() {
		return port;
	}

	@Override
	public String getMessage() {
		return "Opening a serversocket at port " + port + " failed. Please make sure that the port is not occupied by a different program or change your rule configuration to select another one.";
	}

	@Override
	public String getLocalizedMessage() {
		return getMessage();
	}

}
