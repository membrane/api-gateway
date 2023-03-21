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

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import javax.annotation.Nullable;
import javax.net.ssl.SNIServerName;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.oracle.util.ssl.SSLCapabilities;
import com.oracle.util.ssl.SSLExplorer;
import com.predic8.membrane.core.config.ConfigurationException;
import com.predic8.membrane.core.rules.ServiceProxyKey;

/**
 * Manages multiple {@link SSLContext}s using the same port. This is only possible when using SSL with
 * "Server Name Indication", see <a href="http://en.wikipedia.org/wiki/Server_Name_Indication">...</a> .
 * <p>
 * This requires Java 8.
 */
public class SSLContextCollection implements SSLProvider {

	private static final Logger log = LoggerFactory.getLogger(SSLContextCollection.class.getName());

	public static class Builder {
		private List<String> dnsNames = new ArrayList<>();
		private List<SSLContext> sslContexts = new ArrayList<>();

		public SSLProvider build() throws ConfigurationException {
			if (sslContexts.isEmpty())
				throw new IllegalStateException("No SSLContext's were added to this Builder before invoking build().");
			if (sslContexts.size() > 1) {
				return new SSLContextCollection(sslContexts, dnsNames);
			} else
				return sslContexts.get(0);
		}

		public void add(SSLContext sslContext) {
			if (!sslContexts.contains(sslContext)) {
				sslContexts.add(sslContext);
				dnsNames.add(sslContext.constructHostNamePattern());
			}
		}
	}

	private final List<SSLContext> sslContexts;
	private final List<Pattern> dnsNames;

	/**
	 * @param sslContexts
	 *            list of SSLContext
	 * @param dnsNames
	 *            corresponding (=same length, 1:1 mapping) list of dnsName
	 *            strings (same syntax as
	 *            {@link ServiceProxyKey#setHost(String)})
	 */
	private SSLContextCollection(List<SSLContext> sslContexts, List<String> dnsNames) {
		this.dnsNames = new ArrayList<>();
		for (String dnsName : dnsNames)
			this.dnsNames.add(Pattern.compile(ServiceProxyKey.createHostPattern(dnsName), Pattern.CASE_INSENSITIVE));
		this.sslContexts = sslContexts;
	}

	public ServerSocket createServerSocket(int port, int backlog, InetAddress bindAddress) throws IOException {
		return new ServerSocket(port, backlog, bindAddress);
	}

	@Override
	public Socket wrapAcceptedSocket(Socket socket) throws IOException {
		InputStream ins = socket.getInputStream();

		byte[] buffer = new byte[0xFF];
		int position = 0;
		SSLCapabilities capabilities = null;

		//Set socket read timeout to 30 seconds
		socket.setSoTimeout(30000);

		// Read the header of TLS record
		while (position < SSLExplorer.RECORD_HEADER_SIZE) {
			int count = SSLExplorer.RECORD_HEADER_SIZE - position;
			int n = ins.read(buffer, position, count);
			if (n < 0) {
				throw new IOException("unexpected end of stream!");
			}
			position += n;
		}

		// Get the required size to explore the SSL capabilities
		int recordLength = SSLExplorer.getRequiredSize(buffer, 0, position);
		if (buffer.length < recordLength) {
			buffer = Arrays.copyOf(buffer, recordLength);
		}

		while (position < recordLength) {
			int count = recordLength - position;
			int n = ins.read(buffer, position, count);
			if (n < 0) {
				throw new IOException("unexpected end of stream!");
			}
			position += n;
		}

		capabilities = SSLExplorer.explore(buffer, 0, recordLength);

		SSLContext sslContext = null;

		if (capabilities != null) {
			List<SNIServerName> serverNames = capabilities.getServerNames();
			if (serverNames != null && serverNames.size() > 0) {
				OUTER:
					for (SNIServerName snisn : serverNames) {
						String hostname = new String(snisn.getEncoded(), "UTF-8");
						for (int i = 0; i < dnsNames.size(); i++)
							if (dnsNames.get(i).matcher(hostname).matches()) {
								sslContext = sslContexts.get(i);
								break OUTER;
							}
					}
			if (sslContext == null) {
				// no hostname matched: send 'unrecognized_name' alert and close socket

				byte[] alert_unrecognized_name = { 21 /* alert */, 3, 1 /* TLS 1.0 */, 0, 2 /* length: 2 bytes */,
						2 /* fatal */, 112 /* unrecognized_name */ };

				try (socket) {
					socket.getOutputStream().write(alert_unrecognized_name);
				}

				StringBuilder hostname = null;
				for (SNIServerName snisn : serverNames) {
					if (hostname == null)
						hostname = new StringBuilder();
					else
						hostname.append(", ");
					hostname.append(new String(snisn.getEncoded(), "UTF-8"));
				}

				throw new RuntimeException("no certificate configured (sending unrecognized_name alert) for hostname \"" + hostname + "\"");
			}
			}
		}

		// no Server Name Indication used by the client: fall back to first sslContext marked as 'useAsDefault'
		// (or the first, if none is marked)
		if (sslContext == null) {
			for (SSLContext sc : sslContexts)
				if (sc.isUseAsDefault()) {
					sslContext = sc;
					break;
				}
		}
		if (sslContext == null)
			sslContext = sslContexts.get(0);

		return sslContext.wrap(socket, buffer, position);
	}

	private SSLContext getSSLContextForHostname(String hostname) {
		SSLContext sslContext = null;
		for (int i = 0; i < dnsNames.size(); i++)
			if (dnsNames.get(i).matcher(hostname).matches()) {
				sslContext = sslContexts.get(i);
				break;
			}
		if (sslContext == null)
			sslContext = sslContexts.get(0);
		return sslContext;
	}

	@Override
	public Socket createSocket() throws IOException {
		throw new IllegalStateException("not implemented");
	}

	@Override
	public Socket createSocket(String host, int port, InetAddress addr,
			int localPort, int connectTimeout, @Nullable String sniServerName, @Nullable String[] applicationProtocols) throws IOException {
		return getSSLContextForHostname(host).createSocket(host, port, addr, localPort, connectTimeout, sniServerName,
				applicationProtocols);
	}

	@Override
	public boolean showSSLExceptions() {
		for(SSLContext ctx : sslContexts)
			if(ctx.showSSLExceptions() == false)
				return false;
		return true;
	}

	@Override
	public Socket createSocket(String host, int port, int connectTimeout, @Nullable String sniServerName,
							   @Nullable String[] applicationProtocols)
			throws IOException {
		return getSSLContextForHostname(host).createSocket(host, port, connectTimeout, sniServerName,
				applicationProtocols);
	}

	public Socket createSocket(Socket s, String host, int port, int connectTimeout, @Nullable String sniServerName,
							   @Nullable String[] applicationProtocols)
			throws IOException {
		return getSSLContextForHostname(host).createSocket(s, host, port, connectTimeout, sniServerName,
				applicationProtocols);
	}

	@Override
	public String[] getApplicationProtocols(Socket socket) {
		return sslContexts.get(0).getApplicationProtocols(socket);
	}

	@Override
	public void stop() {
		for (SSLContext sslContext : sslContexts) {
			sslContext.stop();
		}
	}
}
