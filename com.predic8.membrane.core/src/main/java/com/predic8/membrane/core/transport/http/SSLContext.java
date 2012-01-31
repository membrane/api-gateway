package com.predic8.membrane.core.transport.http;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocketFactory;

import com.predic8.membrane.core.config.security.SSLParser;

public class SSLContext {
	
	public SSLContext(SSLParser sslParser) {
		// this does just the samething as before. (in future, the system properties should not be used)
		if (sslParser.getKeyStore() != null) {
			if (sslParser.getKeyStore().getLocation() != null)
				System.setProperty("javax.net.ssl.keyStore", sslParser.getKeyStore().getLocation());
			if (sslParser.getKeyStore().getPassword() != null)
				System.setProperty("javax.net.ssl.keyStorePassword", sslParser.getKeyStore().getPassword());
		}
		if (sslParser.getTrustStore() != null) {
			if (sslParser.getTrustStore().getLocation() != null)
				System.setProperty("javax.net.ssl.trustStore", sslParser.getTrustStore().getLocation());
			if (sslParser.getTrustStore().getPassword() != null)
				System.setProperty("javax.net.ssl.trustStorePassword", sslParser.getTrustStore().getPassword());
		}
	}

	public ServerSocket createServerSocket(int port) throws IOException {
		return ((SSLServerSocketFactory) SSLServerSocketFactory.getDefault()).createServerSocket(port);
	}
	
	public Socket createSocket(InetAddress host, int port) throws IOException {
		return SSLSocketFactory.getDefault().createSocket(host, port);
	}
	
	public Socket createSocket(InetAddress host, int port, InetAddress addr, int foo) throws IOException {
		return SSLSocketFactory.getDefault().createSocket(host, port, addr, foo);
	}

}
