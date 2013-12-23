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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import com.oracle.util.ssl.SSLCapabilities;
import com.oracle.util.ssl.SSLExplorer;
import com.predic8.membrane.core.config.ConfigurationException;
import com.predic8.membrane.core.rules.ServiceProxyKey;

/**
 * Manages multiple {@link SSLContext}s using the same port. This is only possible when using SSL with
 * "Server Name Indication", see http://en.wikipedia.org/wiki/Server_Name_Indication .
 * 
 * This requires Java 8 (at runtime only, using reflection and dummy classes for compilation). Using the
 * inner {@link Builder} class, no Java 8 feature will be used, if not required by the configution. If the
 * configuration ("proxies.xml") requires Java 8 and it is not available, an exception will be thrown.
 */
public class SSLContextCollection implements SSLProvider {

	private static Method createSocketMethod;
	
	static {
		try {
			createSocketMethod = SSLSocketFactory.class.getMethod("createSocket", new Class[] { Socket.class,
					InputStream.class, Boolean.TYPE });
		} catch (SecurityException e) {
			throw new RuntimeException(e); 
		} catch (NoSuchMethodException e) {
			// do nothing
		}
	}


	public static class Builder {
		private List<String> dnsNames = new ArrayList<String>();
		private List<SSLContext> sslContexts = new ArrayList<SSLContext>();
		
		public SSLProvider build() throws ConfigurationException {
			if (sslContexts.size() == 0)
				throw new IllegalStateException("No SSLContext's were added to this Builder before invoking build().");
			if (sslContexts.size() > 1) {
				if (createSocketMethod == null)
					throw new ConfigurationException("Using <serviceProxy>s with different <ssl> configurations on the same @port requires a Java 8 runtime (which added support for SSL with Server Name Indication).");
				return new SSLContextCollection(sslContexts, dnsNames);
			} else
				return sslContexts.get(0);
		}

		public void add(SSLContext sslContext, String hostPattern) {
			// TODO: 'hostPattern' is ignored
			if (!sslContexts.contains(sslContext)) {
				sslContexts.add(sslContext);
				dnsNames.add(constructHostNamePattern(sslContext));
			}
		}

		private String constructHostNamePattern(SSLContext sslContext) {
			StringBuilder sb = null; 
			List<String> dnsNames = sslContext.getDnsNames();
			if (dnsNames == null)
				throw new RuntimeException("Could not extract DNS names from the first key's certificate in " + sslContext.getLocation());
			for (String dnsName : dnsNames) {
				if (sb == null)
					sb = new StringBuilder();
				else
					sb.append(" ");
				sb.append(dnsName);
			}
			return sb.toString();
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
		this.dnsNames = new ArrayList<Pattern>();
		for (String dnsName : dnsNames)
			this.dnsNames.add(Pattern.compile(ServiceProxyKey.createHostPattern(dnsName), Pattern.CASE_INSENSITIVE));
		this.sslContexts = sslContexts;
	}
	
	public ServerSocket createServerSocket(int port, int backlog, InetAddress bindAddress) throws IOException {
		return new ServerSocket(port, 50, bindAddress) {
			@Override
			public Socket accept() throws IOException {
				Socket socket = super.accept();
				
				InputStream ins = socket.getInputStream();

				byte[] buffer = new byte[0xFF];
				int position = 0;
				SSLCapabilities capabilities = null;

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

							try {
								socket.getOutputStream().write(alert_unrecognized_name);
							} finally {
								socket.close();
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
				
				// no Server Name Indication used by the client: fall back to first sslContext
				if (sslContext == null)
					sslContext = sslContexts.get(0);

				SSLSocketFactory serviceSocketFac = sslContext.getSocketFactory();

				ByteArrayInputStream bais = new ByteArrayInputStream(buffer, 0, position);
				
				SSLSocket serviceSocket;
				// "serviceSocket = (SSLSocket)serviceSocketFac.createSocket(socket, bais, true);" only compileable with Java 1.8
				try {
					serviceSocket = (SSLSocket) createSocketMethod.invoke(serviceSocketFac, new Object[] { socket, bais, true });
				} catch (IllegalArgumentException e) {
					throw new RuntimeException(e);
				} catch (IllegalAccessException e) {
					throw new RuntimeException(e);
				} catch (InvocationTargetException e) {
					throw new RuntimeException(e);
				}
				
				String[] ciphers = sslContext.getCiphers();
				if (ciphers != null)
					serviceSocket.setEnabledCipherSuites(ciphers);
				serviceSocket.setWantClientAuth(sslContext.isWantClientAuth());
				serviceSocket.setNeedClientAuth(sslContext.isNeedClientAuth());
				
				return serviceSocket;
			}
		};
	}
	
	@Override
	public Socket createSocket(InetAddress host, int port, InetAddress addr,
			int localPort, int connectTimeout) throws IOException {
		SSLContext sslContext = sslContexts.get(0); // what is the correct client behavior?
		// at the moment, for the client side, SSLContextCollection is never constructed
		return sslContext.createSocket(host, port, addr, localPort, connectTimeout);
	}
	
	@Override
	public Socket createSocket(InetAddress host, int port, int connectTimeout)
			throws IOException {
		SSLContext sslContext = sslContexts.get(0); // what is the correct client behavior?
		// at the moment, for the client side, SSLContextCollection is never constructed
		return sslContext.createSocket(host, port, connectTimeout);
	}
}
