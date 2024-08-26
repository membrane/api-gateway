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

import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.config.security.KeyStore;
import com.predic8.membrane.core.config.security.SSLParser;
import com.predic8.membrane.core.config.security.TrustStore;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.platform.commons.util.UnrecoverableExceptions;

import javax.naming.InvalidNameException;
import javax.net.ssl.SSLHandshakeException;
import javax.security.auth.x500.X500Principal;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Optional;

import static com.predic8.membrane.core.transport.ssl.StaticSSLContext.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SSLContextTest {

	private static Router router;

	@BeforeAll
	public static void before() {
		router = new HttpRouter();
	}

	@AfterAll
	public static void done() {
		router.stop();
	}

	private static class SSLContextBuilder {
		private final SSLParser sslParser = new SSLParser();

		public SSLContextBuilder() {
			sslParser.setEndpointIdentificationAlgorithm("");
		}

		public StaticSSLContext build() {
			return new StaticSSLContext(sslParser, router.getResolverMap(), router.getBaseLocation());
		}

		private SSLContextBuilder byKeyAlias(String alias) {
			sslParser.getKeyStore().setKeyAlias(alias);
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

	@Test
	public void keyAliasSelectPresent() throws Exception {
        Optional<String> key1 = fetchKeyAlias(getAliasKeystoreByAlias("key1", router), "key1");
		Optional<String> key2 = fetchKeyAlias(getAliasKeystoreByAlias("key2", router), "key2");
		assertTrue(key1.isPresent());
		assertTrue(key2.isPresent());
		assertEquals("key1", key1.get());
		assertEquals("key2", key2.get());
	}

	@Test
	public void keyAliasDefaultFallback() throws Exception {
		Optional<String> key1 = fetchKeyAlias(getAliasKeystoreByAlias("key1", router), null);
		Optional<String> key2 = fetchKeyAlias(getAliasKeystoreByAlias("key2", router), null);
		assertTrue(key1.isPresent());
		assertTrue(key2.isPresent());
		assertEquals("key1", key1.get());
		assertEquals("key1", key2.get());
	}

	@Test
	public void keyAliasSelectNotPresent() throws Exception {
		Optional<String> key = fetchKeyAlias(getAliasKeystoreByAlias("key3", router), "key3");
		assertTrue(key.isEmpty());
	}

	@Test
	void validX509ReturnsCN() throws InvalidNameException {
		X509Certificate cert = mock(X509Certificate.class);
		when(cert.getSubjectX500Principal()).thenReturn(new X500Principal("CN=John Doe, O=Example Org, C=US"));
		Optional<String> result = getCommonName(cert);
		assertTrue(result.isPresent());
		assertEquals("John Doe", result.get());
	}

	@Test
	void X509WithoutCNReturnsEmpty() throws InvalidNameException {
		X509Certificate cert = mock(X509Certificate.class);
		when(cert.getSubjectX500Principal()).thenReturn(new X500Principal("O=Example Org, C=US"));
		Optional<String> result = getCommonName(cert);
		assertTrue(result.isEmpty());
	}

	@Test
	void nonX509ReturnsEmpty() throws InvalidNameException {
		Certificate cert = mock(Certificate.class);
		Optional<String> result = getCommonName(cert);
		assertTrue(result.isEmpty());
	}

	@Test
	public void simpleConfig() {
		assertThrows(Exception.class, () -> {
			SSLContext server = cb().build();
			SSLContext client = cb().build();
			testCombination(server, client);
		});
	}

	@Test
	public void serverKeyOnlyWithoutClientTrust() {
		assertThrows(Exception.class, () -> {
			SSLContext server = cb().withKeyStore("classpath:/ssl-rsa.keystore").build();
			SSLContext client = cb().build();
			testCombination(server, client);
		});
	}

	@Test
	public void serverKeyOnlyWithClientTrust() throws Exception {
		SSLContext server = cb().withKeyStore("classpath:/ssl-rsa.keystore").build();
		SSLContext client = cb().withTrustStore("classpath:/ssl-rsa-pub.keystore").build();
		testCombination(server, client);
	}

	@Test
	public void serverKeyOnlyWithInvalidClientTrust() {
		assertThrows2(SocketException.class, SSLHandshakeException.class, () -> {
			SSLContext server = cb().withKeyStore("classpath:/ssl-rsa2.keystore").build();
			SSLContext client = cb().withTrustStore("classpath:/ssl-rsa-pub.keystore").build();
			testCombination(server, client);
		});
	}

	@Test
	public void serverAndClientCertificates() throws Exception {
		SSLContext server = cb().withKeyStore("classpath:/ssl-rsa.keystore").withTrustStore("classpath:/ssl-rsa-pub2.keystore").needClientAuth().build();
		SSLContext client = cb().withKeyStore("classpath:/ssl-rsa2.keystore").withTrustStore("classpath:/ssl-rsa-pub.keystore").needClientAuth().build();
		testCombination(server, client);
	}

	@Test
	public void serverAndClientCertificatesWithoutServerTrust() {
		assertThrows(SSLHandshakeException.class, () -> {
			SSLContext server = cb().withKeyStore("classpath:/ssl-rsa.keystore").withTrustStore("classpath:/ssl-rsa-pub.keystore").needClientAuth().build();
			SSLContext client = cb().withKeyStore("classpath:/ssl-rsa2.keystore").withTrustStore("classpath:/ssl-rsa-pub.keystore").needClientAuth().build();
			testCombination(server, client);
		});
	}

	private static @NotNull java.security.KeyStore getAliasKeystoreByAlias(String alias, Router router) throws Exception {
		return openKeyStore(new KeyStore() {{
			setLocation("classpath:/alias-keystore.p12");
			setKeyPassword("secret");
			setKeyAlias(alias);
		}}, "PKCS12", "secret".toCharArray(), router.getResolverMap(), router.getBaseLocation());
	}

	public static <T extends Throwable, S extends Throwable> void assertThrows2(Class<T> expectedType1, Class<S> expectedType2, Executable executable) {
		try {
			executable.execute();
		} catch (Throwable actualException) {
			if (expectedType1.isInstance(actualException)) {
				return;
			} else if (expectedType2.isInstance(actualException)) {
				return;
			} else {
				UnrecoverableExceptions.rethrowIfUnrecoverable(actualException);
				throw new RuntimeException("Unexpected exception type thrown");
			}
		}
		throw new RuntimeException("Expected exception to be thrown, but nothing was thrown.");
	}

	private void testCombination(SSLContext server, final SSLContext client) throws Exception {
		ServerSocket ss = server.createServerSocket(3020, 50, null);

		final Exception[] ex = new Exception[1];

		Thread t = new Thread(() -> {
			try {
				try (Socket s = client.createSocket("localhost", 3020, 30000, null, null)) {
					BufferedReader br = new BufferedReader(new InputStreamReader(s.getInputStream()));
					assertEquals("Hi", br.readLine());
				}
			} catch (Exception e) {
				ex[0] = e;
			}
		});
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
