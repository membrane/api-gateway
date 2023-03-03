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

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.config.security.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.function.Executable;
import org.junit.platform.commons.util.UnrecoverableExceptions;

import javax.net.ssl.*;
import java.io.*;
import java.net.*;

import static java.lang.String.format;
import static org.junit.jupiter.api.AssertionFailureBuilder.assertionFailure;
import static org.junit.jupiter.api.Assertions.*;

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

		public SSLContext build() {
			return new StaticSSLContext(sslParser, router.getResolverMap(), router.getBaseLocation());
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
