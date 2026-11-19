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

package com.predic8.membrane.core.transport.http;

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.proxies.*;
import com.predic8.membrane.core.router.*;
import com.predic8.membrane.core.transport.http.client.*;
import org.apache.commons.io.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;

import java.io.*;
import java.net.*;
import java.util.*;

import static com.predic8.membrane.core.http.Header.KEEP_ALIVE;
import static com.predic8.membrane.core.http.Header.SOAP_ACTION;
import static com.predic8.membrane.core.http.MimeType.TEXT_XML_UTF8;
import static com.predic8.membrane.core.http.Request.METHOD_POST;
import static com.predic8.membrane.core.http.Response.ok;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.interceptor.Outcome.RETURN;
import static java.lang.Thread.sleep;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.apache.commons.lang3.StringUtils.countMatches;
import static org.junit.jupiter.api.Assertions.*;

public class HttpKeepAliveTest {

	private HashSet<Integer> hashs; // tracks the hashcodes of all connections used
	private Router service1;
	private ServiceProxy sp1;

	@BeforeEach
	public void setUp() throws Exception {
		hashs = new HashSet<>();

		service1 = new TestRouter();
		sp1 = new ServiceProxy(new ServiceProxyKey("localhost",
				METHOD_POST, ".*", 2003), "thomas-bayer.com", 80);
		sp1.getFlow().add(new AbstractInterceptor(){
			@Override
			public Outcome handleRequest(Exchange exc) {
                try {
                    exc.getRequest().readBody();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                exc.setResponse(ok("OK.").build());
				hashs.add(((HttpServerHandler)exc.getHandler()).getSrcOut().hashCode());
				return RETURN;
			}
		});
		service1.add(sp1);
		service1.start();
	}

	@AfterEach
	public void tearDown() {
		service1.stop();
	}

	private HttpClient createHttpClient(int defaultKeepAliveTimeout) {
		HttpClientConfiguration configuration = createConfig();
		configuration.getConnection().setKeepAliveTimeout(defaultKeepAliveTimeout);
        return new HttpClient(configuration);
	}

	private HttpClientConfiguration createConfig() {
		HttpClientConfiguration hcc = new HttpClientConfiguration();
		hcc.getConnection().setSoTimeout(80); // this tests that connections kept alive do not die after soTimeout
		return hcc;
	}

	private Exchange createExchange() throws IOException, URISyntaxException {
		return new Request.Builder().
				post("http://localhost:2003/axis2/services/BLZService").
				contentType(TEXT_XML_UTF8).
				header(SOAP_ACTION, "").
				body(IOUtils.toByteArray(this.getClass().getResourceAsStream("/getBank.xml"))).
				buildExchange();
	}

	private int issueRequest(HttpClient client) throws Exception {
		var exchange = createExchange();
        client.call(exchange);
		var response = exchange.getResponse();
		response.readBody();
		return response.getStatusCode();
	}

	@Test
	void keepAlive() throws Exception {
		HttpClient client = new HttpClient(createConfig());

		assertEquals(200, issueRequest(client));
		assertEquals(200, issueRequest(client));

		assertEquals(1, hashs.size());
	}

	/**
	 * RFC 9112 2.2: an empty line before the request line is ignored. Here it is a CRLF after the first body that
	 * Content-Length doesn't count, so the second request on the connection is still answered.
	 */
	@Test
	void emptyLineBeforeNextRequestIsIgnored() throws Exception {
		try (final var socket = new Socket("localhost", 2003)) {
			socket.setSoTimeout(5000);
			final var out = socket.getOutputStream();
			out.write("""
					POST /foo HTTP/1.1\r
					Host: localhost:2003\r
					Content-Length: 3\r
					\r
					abc\r
					POST /foo HTTP/1.1\r
					Host: localhost:2003\r
					Content-Length: 3\r
					Connection: close\r
					\r
					abc""".getBytes(US_ASCII));
			out.flush();

			final var responses = new String(socket.getInputStream().readAllBytes(), US_ASCII);
			assertEquals(2, countMatches(responses, "HTTP/1.1 200 "), responses);
		}
	}

	/**
	 * RFC 9112 2.2: a bare LF ends a line. The request is answered without the client sending another byte after
	 * the empty line that ends the header section (#3378).
	 */
	@Test
	void bareLfRequestIsAnswered() throws Exception {
		final var response = sendRaw("POST /foo HTTP/1.1\nHost: localhost:2003\nContent-Length: 0\nConnection: close\n\n");
		assertTrue(response.startsWith("HTTP/1.1 200 "), response);
	}

	/**
	 * RFC 9112 2.2: a bare CR is rejected instead of being read as a line terminator (#3378). Before, the CR ended
	 * the line and the byte after it was dropped, so each of these requests was answered with 200.
	 */
	@ParameterizedTest
	@ValueSource(strings = {
			"POST /foo HTTP/1.1\r_Host: localhost:2003\r\nContent-Length: 0\r\nConnection: close\r\n\r\n",
			"POST /foo HTTP/1.1\r\nHost: localhost:2003\r\nX-A: 1\r X-B: 2\r\nContent-Length: 0\r\nConnection: close\r\n\r\n",
			"POST /foo HTTP/1.1\n\rHost: localhost:2003\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
	})
	void bareCrIsRejected(String request) throws Exception {
		final var response = sendRaw(request);
		assertTrue(response.startsWith("HTTP/1.1 400 "), response);
	}

	/**
	 * The rest of a request body left unread on the connection would be read as the next request, so the
	 * connection is closed. The response says so, whichever plugin left the body unread.
	 */
	@Test
	void responseToRequestWithUnreadBodyAnnouncesClose() throws Exception {
		sp1.getFlow().addFirst(new AbstractInterceptor() {
			@Override
			public Outcome handleRequest(Exchange exc) {
				exc.getRequest().getBody().readUpTo(3);
				exc.setResponse(Response.statusCode(403).build());
				return RETURN;
			}
		});

		final var response = sendRaw("POST /foo HTTP/1.1\r\nHost: localhost:2003\r\nContent-Length: 10\r\n\r\n0123456789");

		assertTrue(response.startsWith("HTTP/1.1 403 "), response);
		assertTrue(response.toLowerCase().contains("\r\nconnection: close\r\n"), response);
	}

	/**
	 * Sends the bytes without closing the output, so a server that waits for more input times out the read.
	 */
	private static String sendRaw(String request) throws IOException {
		try (final var socket = new Socket("localhost", 2003)) {
			socket.setSoTimeout(3000);
			socket.getOutputStream().write(request.getBytes(US_ASCII));
			socket.getOutputStream().flush();
			return new String(socket.getInputStream().readAllBytes(), US_ASCII);
		}
	}

	@Test
	public void testTimeoutDefault() throws Exception {
		HttpClient client = createHttpClient(1000);

		sp1.getFlow().addFirst(new AbstractInterceptor() {
			@Override
			public Outcome handleResponse(Exchange exc) {
				exc.getResponse().getHeader().add(KEEP_ALIVE, "max=2");
				return CONTINUE;
			}
		});

		assertEquals(200, issueRequest(client));
		assertEquals(1, hashs.size());

		sleep(1500);

		assertEquals(200, issueRequest(client));
		assertEquals(2, hashs.size());
	}

	@Test
	void connectionClose() throws Exception {
		HttpClient client = createHttpClient(500);

		sp1.getFlow().addFirst(new AbstractInterceptor() {
			@Override
			public Outcome handleResponse(Exchange exc) {
				exc.getResponse().getHeader().add(KEEP_ALIVE, "max=2");
				return CONTINUE;
			}
		});

		assertEquals(200, issueRequest(client)); // opens connection 1
		assertEquals(1, hashs.size());

		sleep(200);

		assertEquals(1, client.getConnectionFactory().getConnectionManager().getNumberInPool());
		sleep(600); // connection closer did not yet run (runs at 2*keep_alive_timeout)
		// connection 1 is now dead, but still in pool
		assertEquals(1, client.getConnectionFactory().getConnectionManager().getNumberInPool());

		assertEquals(200, issueRequest(client)); // opens connection 2
		assertEquals(2, hashs.size());

		sleep(600); // connection closer runs and closes both
		assertEquals(0, client.getConnectionFactory().getConnectionManager().getNumberInPool());
	}

	@Test
	void timeoutCustom() throws Exception {
		HttpClient client = createHttpClient(1000);

		sp1.getFlow().addFirst(new AbstractInterceptor() {
			@Override
			public Outcome handleResponse(Exchange exc) {
				exc.getResponse().getHeader().add(KEEP_ALIVE, "timeout=1,max=2");
				return CONTINUE;
			}
		});

		assertEquals(200, issueRequest(client));
		assertEquals(1, hashs.size());

		sleep(1500);

		assertEquals(200, issueRequest(client));
		assertEquals(2, hashs.size());

		assertEquals(200, issueRequest(client));
		assertEquals(2, hashs.size());

		assertEquals(200, issueRequest(client));
		assertEquals(3, hashs.size());
	}

	@Test
	public void testMaxParameter() throws Exception {
		HttpClient client = new HttpClient();

		sp1.getFlow().addFirst(new AbstractInterceptor() {
			@Override
			public Outcome handleResponse(Exchange exc) {
				exc.getResponse().getHeader().add(KEEP_ALIVE, "max=2");
				return CONTINUE;
			}
		});

		assertEquals(200, issueRequest(client));
		assertEquals(200, issueRequest(client));
		assertEquals(1, hashs.size());

		assertEquals(200, issueRequest(client));
		assertEquals(2, hashs.size());

		assertEquals(200, issueRequest(client));
		assertEquals(2, hashs.size());

		assertEquals(200, issueRequest(client));
		assertEquals(3, hashs.size());
	}

}
