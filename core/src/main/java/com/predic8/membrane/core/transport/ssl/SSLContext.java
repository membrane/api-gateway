/* Copyright 2012 predic8 GmbH, www.predic8.com

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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.InvalidParameterException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.google.common.base.Objects;
import com.google.common.collect.Sets;
import com.predic8.membrane.core.config.security.SSLParser;
import com.predic8.membrane.core.config.security.Store;
import com.predic8.membrane.core.resolver.ResolverMap;
import com.predic8.membrane.core.transport.TrustManagerWrapper;

public class SSLContext implements SSLProvider {
	
	private static final Log log = LogFactory.getLog(SSLContext.class.getName());

	private static Method setUseCipherSuitesOrderMethod;
	
	static {
		try {
			setUseCipherSuitesOrderMethod = SSLParameters.class.getMethod("setUseCipherSuitesOrder", new Class[] { Boolean.TYPE });
		} catch (SecurityException e) {
			throw new RuntimeException(e); 
		} catch (NoSuchMethodException e) {
			// do nothing
		}
	}


	private final SSLParser sslParser;
	private List<String> dnsNames;

	private final javax.net.ssl.SSLContext sslc;
	private final String[] ciphers;
	private final String[] protocols;
	private final boolean wantClientAuth, needClientAuth;
	
	
	public SSLContext(SSLParser sslParser, ResolverMap resourceResolver, String baseLocation) {
		this.sslParser = sslParser;
		try {
			String algorihm = KeyManagerFactory.getDefaultAlgorithm();
			if (sslParser.getAlgorithm() != null)
				algorihm = sslParser.getAlgorithm();
			
			KeyManagerFactory kmf = null;
			String keyStoreType = "JKS";
			if (sslParser.getKeyStore() != null) {
				if (sslParser.getKeyStore().getKeyAlias() != null)
					throw new InvalidParameterException("keyAlias is not yet supported.");
				char[] keyPass = "changeit".toCharArray();
				if (sslParser.getKeyStore().getKeyPassword() != null)
					keyPass = sslParser.getKeyStore().getKeyPassword().toCharArray();

				if (sslParser.getKeyStore().getType() != null)
					keyStoreType = sslParser.getKeyStore().getType();
				KeyStore ks = openKeyStore(sslParser.getKeyStore(), "JKS", keyPass, resourceResolver, baseLocation);
				kmf = KeyManagerFactory.getInstance(algorihm);
				kmf.init(ks, keyPass);
				
				Enumeration<String> aliases = ks.aliases();
				while (aliases.hasMoreElements()) {
					String alias = aliases.nextElement();
					if (ks.isKeyEntry(alias)) {
						// first key is used by the KeyManagerFactory
						Certificate c = ks.getCertificate(alias);
						if (c instanceof X509Certificate) {
							X509Certificate x = (X509Certificate) c;
							
							dnsNames = new ArrayList<String>();

							Collection<List<?>> subjectAlternativeNames = x.getSubjectAlternativeNames();
							if (subjectAlternativeNames != null)
								for (List<?> l : subjectAlternativeNames) {
									if (l.get(0) instanceof Integer && ((Integer)l.get(0) == 2))
										dnsNames.add(l.get(1).toString());
								}
						}
						break;
					}
				}

			}
			TrustManagerFactory tmf = null;
			if (sslParser.getTrustStore() != null) {
				String trustAlgorithm = TrustManagerFactory.getDefaultAlgorithm();
				if (sslParser.getTrustStore().getAlgorithm() != null)
					trustAlgorithm = sslParser.getTrustStore().getAlgorithm();
				KeyStore ks = openKeyStore(sslParser.getTrustStore(), keyStoreType, null, resourceResolver, baseLocation);
				tmf = TrustManagerFactory.getInstance(trustAlgorithm);
				tmf.init(ks);
			}
			
			TrustManager[] tms = tmf != null ? tmf.getTrustManagers() : null /* trust anyone: new TrustManager[] { new NullTrustManager() } */;
			if (sslParser.isIgnoreTimestampCheckFailure())
				tms = new TrustManager[] { new TrustManagerWrapper(tms, true) };
			
			if (sslParser.getProtocol() != null)
				sslc = javax.net.ssl.SSLContext.getInstance(sslParser.getProtocol());
			else
				sslc = javax.net.ssl.SSLContext.getInstance("TLS");
			
			sslc.init(
					kmf != null ? kmf.getKeyManagers() : null, 
					tms, 
					null);
			
			if (sslParser.getCiphers() != null) {
				ciphers = sslParser.getCiphers().split(",");
				Set<String> supportedCiphers = Sets.newHashSet(sslc.getSocketFactory().getSupportedCipherSuites());
				for (String cipher : ciphers) {
					if (!supportedCiphers.contains(cipher))
						throw new InvalidParameterException("Unknown cipher " + cipher);
					if (cipher.contains("_RC4_"))
						log.warn("Cipher " + cipher + " uses RC4, which is deprecated.");
				}
			} else {
				// use all default ciphers except those using RC4
				String supportedCiphers[] = sslc.getSocketFactory().getDefaultCipherSuites();				
				ArrayList<String> ciphers = new ArrayList<String>(supportedCiphers.length);
				for (String cipher : supportedCiphers)
					if (!cipher.contains("_RC4_"))
						ciphers.add(cipher);
				sortCiphers(ciphers);
				this.ciphers = ciphers.toArray(new String[ciphers.size()]);
			}
			if (setUseCipherSuitesOrderMethod == null)
				log.warn("Cannot set the cipher suite order before Java 8. This prevents Forward Secrecy with some SSL clients.");
			
			if (sslParser.getProtocols() != null) {
				protocols = sslParser.getProtocols().split(","); 
			} else {
				protocols = null;
			}
			
			if (sslParser.getClientAuth() == null) {
				needClientAuth = false;
				wantClientAuth = false;
			} else if (sslParser.getClientAuth().equals("need")) {
				needClientAuth = true;
				wantClientAuth = true;
			} else if (sslParser.getClientAuth().equals("want")) {
				needClientAuth = false;
				wantClientAuth = true;
			} else {
				throw new RuntimeException("Invalid value '"+sslParser.getClientAuth()+"' in clientAuth: expected 'want', 'need' or not set.");
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	private static class CipherInfo {
		public final String cipher;
		public final int points;
		
		public CipherInfo(String cipher) {
			this.cipher = cipher;
			int points = 0;
			if (supportsPFS(cipher))
				points = 1;
			
			this.points = points;
		}

		private boolean supportsPFS(String cipher2) {
			// see https://en.wikipedia.org/wiki/Forward_secrecy#Protocols
			return cipher.contains("_DHE_RSA_") || cipher.contains("_DHE_DSS_") || cipher.contains("_ECDHE_RSA_") || cipher.contains("_ECDHE_ECDSA_");
		}
	}
	
	private void sortCiphers(ArrayList<String> ciphers) {
		ArrayList<CipherInfo> cipherInfos = new ArrayList<SSLContext.CipherInfo>(ciphers.size());
		
		for (String cipher : ciphers)
			cipherInfos.add(new CipherInfo(cipher));
		
		Collections.sort(cipherInfos, new Comparator<CipherInfo>() {
			@Override
			public int compare(CipherInfo cipher1, CipherInfo cipher2) {
				return cipher2.points - cipher1.points;
			}
		});
		
		for (int i = 0; i < ciphers.size(); i++)
			ciphers.set(i, cipherInfos.get(i).cipher);
	}
	
	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof SSLContext))
			return false;
		SSLContext other = (SSLContext)obj;
		return Objects.equal(sslParser, other.sslParser);
	}
	
	private KeyStore openKeyStore(Store store, String defaultType, char[] keyPass, ResolverMap resourceResolver, String baseLocation) throws NoSuchAlgorithmException, CertificateException, FileNotFoundException, IOException, KeyStoreException, NoSuchProviderException {
		String type = store.getType();
		if (type == null)
			type = defaultType;
		char[] password = keyPass;
		if (store.getPassword() != null)
			password = store.getPassword().toCharArray();
		if (password == null)
			throw new InvalidParameterException("Password for key store is not set.");
		KeyStore ks;
		if (store.getProvider() != null)
			ks = KeyStore.getInstance(type, store.getProvider());
		else
			ks = KeyStore.getInstance(type);
		ks.load(resourceResolver.resolve(ResolverMap.combine(baseLocation, store.getLocation())), password);
		return ks;
	}
	
	public void applyCiphers(SSLSocket sslSocket) {
		if (ciphers != null) {
			SSLParameters sslParameters = sslSocket.getSSLParameters();
			applyCipherOrdering(sslParameters);
			sslParameters.setCipherSuites(ciphers);
			sslSocket.setSSLParameters(sslParameters);
		}
	}
	
	public void applyCiphers(SSLServerSocket sslServerSocket) {
		if (ciphers != null) {
			SSLParameters sslParameters = sslServerSocket.getSSLParameters();
			applyCipherOrdering(sslParameters);
			sslParameters.setCipherSuites(ciphers);
			sslServerSocket.setSSLParameters(sslParameters);
		}
	}
	
	private void applyCipherOrdering(SSLParameters sslParameters) {
		// "sslParameters.setUseCipherSuitesOrder(true);" works only on Java 8
		try {
			if (setUseCipherSuitesOrderMethod != null)
				setUseCipherSuitesOrderMethod.invoke(sslParameters, true);
		} catch (IllegalAccessException e) {
			throw new RuntimeException(e);
		} catch (IllegalArgumentException e) {
			throw new RuntimeException(e);
		} catch (InvocationTargetException e) {
			throw new RuntimeException(e);
		}
	}

	public ServerSocket createServerSocket(int port, int backlog, InetAddress bindAddress) throws IOException {
		SSLServerSocketFactory sslssf = sslc.getServerSocketFactory();
		SSLServerSocket sslss = (SSLServerSocket) sslssf.createServerSocket(port, backlog, bindAddress);
		applyCiphers(sslss);
		if (protocols != null) {
			sslss.setEnabledProtocols(protocols);
		} else {
			String[] protocols = sslss.getEnabledProtocols();
			Set<String> set = new HashSet<String>();
			for (String protocol : protocols) {
				if (protocol.equals("SSLv3") || protocol.equals("SSLv2Hello")) {
					continue;
				}
				set.add(protocol);
			}
			sslss.setEnabledProtocols(set.toArray(new String[0]));
		}
		sslss.setWantClientAuth(wantClientAuth);
		sslss.setNeedClientAuth(needClientAuth);
		return sslss;
	}
	
	public Socket wrapAcceptedSocket(Socket socket) throws IOException {
		return socket;
	}
	
	public Socket createSocket(InetAddress host, int port, int connectTimeout) throws IOException {
		Socket s = new Socket();
		s.connect(new InetSocketAddress(host, port), connectTimeout);
		SSLSocketFactory sslsf = sslc.getSocketFactory();
		SSLSocket ssls = (SSLSocket) sslsf.createSocket(s, host.getHostName(), port, true);
		if (protocols != null) {
			ssls.setEnabledProtocols(protocols);
		} else {
			String[] protocols = ssls.getEnabledProtocols();
			Set<String> set = new HashSet<String>();
			for (String protocol : protocols) {
				if (protocol.equals("SSLv3") || protocol.equals("SSLv2Hello")) {
					continue;
				}
				set.add(protocol);
			}
			ssls.setEnabledProtocols(set.toArray(new String[0]));
		}
		applyCiphers(ssls);
		return ssls;
	}
	
	public Socket createSocket(InetAddress host, int port, InetAddress addr, int localPort, int connectTimeout) throws IOException {
		Socket s = new Socket();
		s.bind(new InetSocketAddress(addr, localPort));
		s.connect(new InetSocketAddress(host, port), connectTimeout);
		SSLSocketFactory sslsf = sslc.getSocketFactory();
		SSLSocket ssls = (SSLSocket) sslsf.createSocket(s, host.getHostName(), port, true);
		applyCiphers(ssls);
		if (protocols != null) {
			ssls.setEnabledProtocols(protocols);
		} else {
			String[] protocols = ssls.getEnabledProtocols();
			Set<String> set = new HashSet<String>();
			for (String protocol : protocols) {
				if (protocol.equals("SSLv3") || protocol.equals("SSLv2Hello")) {
					continue;
				}
				set.add(protocol);
			}
			ssls.setEnabledProtocols(set.toArray(new String[0]));
		}
		return ssls;
	}
	
	SSLSocketFactory getSocketFactory() {
		return sslc.getSocketFactory();
	}
	
	String[] getCiphers() {
		return ciphers;
	}
	
	String[] getProtocols() {
		return protocols;
	}
	
	boolean isNeedClientAuth() {
		return needClientAuth;
	}
	
	boolean isWantClientAuth() {
		return wantClientAuth;
	}
	
	List<String> getDnsNames() {
		return dnsNames;
	}

	/**
	 * 
	 * @return Human-readable description of there the keystore lives.
	 */
	String getLocation() {
		return sslParser.getKeyStore() != null ? sslParser.getKeyStore().getLocation() : "null";
	}
}
