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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

import javax.net.ssl.SSLHandshakeException;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.config.security.KeyStore;
import com.predic8.membrane.core.config.security.SSLParser;
import com.predic8.membrane.core.config.security.TrustStore;
import com.predic8.membrane.core.transport.ssl.SSLContext;

public class SSLContextTest {

	private Router router;

	@Before
	public void before() {
		router = new HttpRouter();
	}

	private class SSLContextBuilder {
		private SSLParser sslParser = new SSLParser();

		public SSLContextBuilder() {
			sslParser.setEndpointIdentificationAlgorithm("");
		}

		public SSLContext build() {
			return new SSLContext(sslParser, router.getResolverMap(), router.getBaseLocation());
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
			sslParser.setKeyStore(new KeyStore());
			sslParser.getKeyStore().setLocation(keystore);
			sslParser.getKeyStore().setKeyPassword("secret");
			return this;
		}

		private SSLContextBuilder withTrustStore(String keystore) {
			sslParser.setTrustStore(new TrustStore());
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
		ServerSocket ss = server.createServerSocket(3020, 50, null);

		final Exception ex[] = new Exception[1];

		Thread t = new Thread(new Runnable(){
			@Override
			public void run() {
				try {
					Socket s = client.createSocket(InetAddress.getLocalHost(), 3020, 30000);
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
