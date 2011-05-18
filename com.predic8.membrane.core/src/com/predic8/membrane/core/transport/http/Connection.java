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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import javax.net.ssl.SSLSocket;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class Connection {
	
	private static Log log = LogFactory.getLog(Connection.class.getName());
	
	public Socket socket;
	public InputStream in;
	public OutputStream out;

	public boolean isSame(String host, int port) {
		return (host.equalsIgnoreCase(socket.getInetAddress().getHostName()) || host.equals(socket.getInetAddress().getHostAddress())) && port == socket.getPort();
	}

	public void close() throws IOException {
		if (isClosed())
			return;
		
		log.debug("Closing HTTP connection LocalPort: " + socket.getLocalPort());
		
		if (in != null)
			in.close();
	
		if (out != null) {
			out.flush();
			out.close();
		}
	
		// Test for isClosed() is needed!
		if (!(socket instanceof SSLSocket) && !socket.isClosed())
			socket.shutdownInput();
		
		socket.close();
	}

	public boolean isClosed() {
		return socket == null || socket.isClosed();
	}
}