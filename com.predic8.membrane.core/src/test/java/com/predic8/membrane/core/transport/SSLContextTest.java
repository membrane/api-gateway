package com.predic8.membrane.core.transport;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;

import javax.net.ssl.SSLHandshakeException;

import junit.framework.Assert;

import org.joda.time.DateMidnight.Property;
import org.junit.Before;
import org.junit.Test;

import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.config.security.KeyStore;
import com.predic8.membrane.core.config.security.SSLParser;
import com.predic8.membrane.core.config.security.TrustStore;

public class SSLContextTest {
	
	private Router router;
	
	@Before
	public void before() {
		router = new HttpRouter();
	}
	
	private class SSLContextBuilder {
		private SSLParser sslParser = new SSLParser(router);
		
		public SSLContext build() {
			return new SSLContext(sslParser, router.getResourceResolver());
		}
		
		private SSLContextBuilder withCiphers(String ciphers) {
			sslParser.setCiphers(ciphers);
			return this;
		}

		private SSLContextBuilder needClientAuth() {
			sslParser.setClientAuth("need");
			return this;
		}

		private SSLContextBuilder withKeyStore(String keystore) {
			sslParser.setKeyStore(new KeyStore(router));
			sslParser.getKeyStore().setLocation(keystore);
			sslParser.getKeyStore().setKeyPassword("secret");
			return this;
		}

		private SSLContextBuilder withTrustStore(String keystore) {
			sslParser.setTrustStore(new TrustStore(router));
			sslParser.getTrustStore().setLocation(keystore);
			sslParser.getTrustStore().setPassword("secret");
			return this;
		}
	}
	
	private SSLContextBuilder cb() {
		return new SSLContextBuilder();
	}
	
	@Test(expected=Exception.class)
	public void simpleConfig() throws Exception {
		SSLContext server = cb().build();
		SSLContext client = cb().build();
		testCombination(server, client);
	}

	@Test
	public void simpleConfigWithWeakCipher() throws Exception {
		if (System.getProperty("java.specification.version").startsWith("1.6"))
			return; // throws "Unknown cipher" elsewise
		SSLContext server = cb().withCiphers("TLS_ECDH_anon_WITH_RC4_128_SHA").build();
		SSLContext client = cb().withCiphers("TLS_ECDH_anon_WITH_RC4_128_SHA").build();
		testCombination(server, client);
	}

	@Test(expected=Exception.class)
	public void serverKeyOnlyWithoutClientTrust() throws Exception {
		SSLContext server = cb().withKeyStore("classpath:/ssl-rsa.keystore").build();
		SSLContext client = cb().build();
		testCombination(server, client);
	}
	
	@Test
	public void serverKeyOnlyWithClientTrust() throws Exception {
		SSLContext server = cb().withKeyStore("classpath:/ssl-rsa.keystore").build();
		SSLContext client = cb().withTrustStore("classpath:/ssl-rsa-pub.keystore").build();
		testCombination(server, client);
	}

	@Test(expected=SSLHandshakeException.class)
	public void serverKeyOnlyWithInvalidClientTrust() throws Exception {
		SSLContext server = cb().withKeyStore("classpath:/ssl-rsa2.keystore").build();
		SSLContext client = cb().withTrustStore("classpath:/ssl-rsa-pub.keystore").build();
		testCombination(server, client);
	}

	@Test
	public void serverAndClientCertificates() throws Exception {
		SSLContext server = cb().withKeyStore("classpath:/ssl-rsa.keystore").withTrustStore("classpath:/ssl-rsa-pub2.keystore").needClientAuth().build();
		SSLContext client = cb().withKeyStore("classpath:/ssl-rsa2.keystore").withTrustStore("classpath:/ssl-rsa-pub.keystore").needClientAuth().build();
		testCombination(server, client);
	}

	@Test(expected=SSLHandshakeException.class)
	public void serverAndClientCertificatesWithoutServerTrust() throws Exception {
		SSLContext server = cb().withKeyStore("classpath:/ssl-rsa.keystore").withTrustStore("classpath:/ssl-rsa-pub.keystore").needClientAuth().build();
		SSLContext client = cb().withKeyStore("classpath:/ssl-rsa2.keystore").withTrustStore("classpath:/ssl-rsa-pub.keystore").needClientAuth().build();
		testCombination(server, client);
	}


	private void testCombination(SSLContext server, final SSLContext client)
			throws IOException, InterruptedException, Exception {
		ServerSocket ss = server.createServerSocket(3020);
		
		final Exception ex[] = new Exception[1];
		
		Thread t = new Thread(new Runnable(){
			@Override
			public void run() {
				try {
					Socket s = client.createSocket(InetAddress.getLocalHost(), 3020);
					try {
						BufferedReader br = new BufferedReader(new InputStreamReader(s.getInputStream()));
						Assert.assertEquals("Hi", br.readLine());
					} finally {
						s.close();
					}
				} catch (Exception e) {
					ex[0] = e;
				}
			}});
		t.start();
		
		try {
			Socket s = ss.accept();
			s.getOutputStream().write("Hi\n".getBytes());
			s.getOutputStream().flush();
		} finally {
			ss.close();
		}
		
		t.join();
		
		if (ex[0] != null)
			throw ex[0];
	}

}
