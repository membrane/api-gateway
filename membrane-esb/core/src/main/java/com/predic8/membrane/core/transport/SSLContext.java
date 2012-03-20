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

package com.predic8.membrane.core.transport;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.InvalidParameterException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.cert.CertificateException;
import java.util.Set;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

import com.google.common.collect.Sets;
import com.predic8.membrane.core.config.security.SSLParser;
import com.predic8.membrane.core.config.security.Store;
import com.predic8.membrane.core.util.ResourceResolver;

public class SSLContext {
	private final javax.net.ssl.SSLContext sslc;
	private final String[] ciphers;
	private final boolean wantClientAuth, needClientAuth;
	
	public SSLContext(SSLParser sslParser, ResourceResolver resourceResolver) {
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
				KeyStore ks = openKeyStore(sslParser.getKeyStore(), "JKS", keyPass, resourceResolver);
				kmf = KeyManagerFactory.getInstance(algorihm);
				kmf.init(ks, keyPass);
			}
			TrustManagerFactory tmf = null;
			if (sslParser.getTrustStore() != null) {
				String trustAlgorithm = TrustManagerFactory.getDefaultAlgorithm();
				if (sslParser.getTrustStore().getAlgorithm() != null)
					trustAlgorithm = sslParser.getTrustStore().getAlgorithm();
				KeyStore ks = openKeyStore(sslParser.getTrustStore(), keyStoreType, null, resourceResolver);
				tmf = TrustManagerFactory.getInstance(trustAlgorithm);
				tmf.init(ks);
			}
			
			if (sslParser.getProtocol() != null)
				sslc = javax.net.ssl.SSLContext.getInstance(sslParser.getProtocol());
			else
				sslc = javax.net.ssl.SSLContext.getInstance("TLS");
			
			sslc.init(
					kmf != null ? kmf.getKeyManagers() : null, 
					tmf != null ? tmf.getTrustManagers() : null /* trust anyone: new TrustManager[] { new NullTrustManager() } */, 
					null);
			
			if (sslParser.getCiphers() != null) {
				ciphers = sslParser.getCiphers().split(",");
				Set<String> supportedCiphers = Sets.newHashSet(sslc.getSocketFactory().getSupportedCipherSuites());
				for (String cipher : ciphers)
					if (!supportedCiphers.contains(cipher))
						throw new InvalidParameterException("Unknown cipher " + cipher);
			} else {
				ciphers = null;
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
	
	private KeyStore openKeyStore(Store store, String defaultType, char[] keyPass, ResourceResolver resourceResolver) throws NoSuchAlgorithmException, CertificateException, FileNotFoundException, IOException, KeyStoreException, NoSuchProviderException {
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
		ks.load(resourceResolver.resolve(store.getLocation()), password);
		return ks;
	}
	
	public ServerSocket createServerSocket(int port) throws IOException {
		SSLServerSocketFactory sslssf = sslc.getServerSocketFactory();
		SSLServerSocket sslss = (SSLServerSocket) sslssf.createServerSocket(port);
		if (ciphers != null)
			sslss.setEnabledCipherSuites(ciphers);
		sslss.setWantClientAuth(wantClientAuth);
		sslss.setNeedClientAuth(needClientAuth);
		return sslss;
	}
	
	public Socket createSocket(InetAddress host, int port) throws IOException {
		SSLSocketFactory sslsf = sslc.getSocketFactory();
		SSLSocket ssls = (SSLSocket) sslsf.createSocket(host, port);
		if (ciphers != null)
			ssls.setEnabledCipherSuites(ciphers);
		return ssls;
	}
	
	public Socket createSocket(InetAddress host, int port, InetAddress addr, int foo) throws IOException {
		SSLSocketFactory sslsf = sslc.getSocketFactory();
		SSLSocket ssls = (SSLSocket) sslsf.createSocket(host, port, addr, foo);
		if (ciphers != null)
			ssls.setEnabledCipherSuites(ciphers);
		return ssls;
	}

}
