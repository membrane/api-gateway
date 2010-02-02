/* Copyright 2009 predic8 GmbH, www.predic8.com

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
import java.util.Enumeration;
import java.util.Hashtable;

import com.predic8.membrane.core.model.IPortChangeListener;
import com.predic8.membrane.core.transport.Transport;

public class HttpTransport extends Transport {

	public static final String SOURCE_HOSTNAME = "com.predic8.membrane.transport.http.source.Hostname";
	public static final String HEADER_HOST = "com.predic8.membrane.transport.http.header.Host";
	public static final String SOURCE_IP = "com.predic8.membrane.transport.http.source.Ip";

	public Hashtable<Integer, HttpEndpointListener> portListenerMapping = new Hashtable<Integer, HttpEndpointListener>();
	
	public synchronized void addPort(int port) throws IOException {

		if (isAnyThreadListeningAt(port)) {
			throw new RuntimeException("Port already used: " + port);
		}

		HttpEndpointListener portListenerThread = new HttpEndpointListener(port, this);
		portListenerMapping.put(new Integer(port), portListenerThread);
		portListenerThread.start();

		for (IPortChangeListener listener : menuListeners) {
			listener.addPort(port);
		}
	}
	
	public boolean isAnyThreadListeningAt(int port) {
		return portListenerMapping.get(port) != null;
	}

	public Enumeration<Integer> getAllPorts() {
		return portListenerMapping.keys();
	}

	public synchronized void closePort(int port) throws IOException {
		HttpEndpointListener plt = portListenerMapping.get(new Integer(port));
		if (plt != null) {
			plt.closePort();
			portListenerMapping.remove(new Integer(port));

			for (IPortChangeListener listener : menuListeners) {
				listener.removePort(port);
			}
		}
	}

	public synchronized void closeAll() throws IOException {

		Enumeration<Integer> enumeration = getAllPorts();
		while (enumeration.hasMoreElements()) {
			Integer p = enumeration.nextElement();
			closePort(p.intValue());
		}
	}

	/**
	 * @param port
	 * @throws IOException
	 */
	public void openPort(int port) throws IOException {
		if (isAnyThreadListeningAt(port))
			return;
		addPort(port);

	}

}
