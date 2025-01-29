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

package com.predic8.membrane.core.transport.ssl;

import com.predic8.membrane.core.util.*;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

public interface SSLProvider {
	ServerSocket createServerSocket(int port, int backlog, InetAddress bindAddress) throws IOException;
	Socket wrapAcceptedSocket(Socket socket) throws IOException, EndOfStreamException;
	Socket createSocket() throws IOException;
	/**
	 * @param host relevant to verify the server cert only
	 * @param port relevant to verify the server cert only
	 */
    Socket createSocket(Socket s, String host, int port, int connectTimeout, @Nullable String sniServerName,
                        @Nullable String[] applicationProtocols) throws IOException;
	Socket createSocket(String host, int port, int connectTimeout, @Nullable String sniServerName,
                        @Nullable String[] applicationProtocols) throws IOException;
	Socket createSocket(String host, int port, InetAddress addr, int localPort, int connectTimeout,
                        @Nullable String sniServerName, @Nullable String[] applicationProtocols) throws IOException;

	boolean showSSLExceptions();

	/**
	 * @return the application protocols selected by the TLS server, or null else wise (not a TLS connection, ALPN not supported, ...)
	 */
    String[] getApplicationProtocols(Socket socket);

	/**
	 * Advises the SSL Provider to stop providing its services. (For most providers this will be a no-op.)
	 * <p>
	 * No guarantee that this is only called once.
	 */
    void stop();
}
