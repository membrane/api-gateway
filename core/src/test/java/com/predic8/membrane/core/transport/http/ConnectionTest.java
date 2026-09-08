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


import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Body;
import com.predic8.membrane.core.http.ReadingBodyException;
import com.predic8.membrane.core.http.ThrowingInputStream;
import com.predic8.membrane.core.proxies.ServiceProxy;
import com.predic8.membrane.core.proxies.ServiceProxyKey;
import com.predic8.membrane.core.router.Router;
import com.predic8.membrane.core.router.TestRouter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.channels.ClosedChannelException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ConnectionTest {

	Connection conLocalhost;
	Connection con127_0_0_1;

	Router router;

	@BeforeEach
	void setUp() throws Exception {

		ServiceProxy proxy2000 = new ServiceProxy(new ServiceProxyKey("localhost", "*", ".*", 2000), "predic8.com", 80);
		router = new TestRouter();
		router.add(proxy2000);
		router.start();

		conLocalhost = Connection.open("localhost", 2000, null, null, 30000);
		con127_0_0_1 = Connection.open("127.0.0.1", 2000, null, null, 30000);
	}

	@AfterEach
	void tearDown() throws Exception {
		conLocalhost.close();
		con127_0_0_1.close();
		assertTrue(conLocalhost.isClosed());
		assertTrue(con127_0_0_1.isClosed());

		router.stop();
	}


	@Test
	public void testIsSame() {
		assertTrue(conLocalhost.isSame("localhost", 2000));
		assertTrue(con127_0_0_1.isSame("127.0.0.1", 2000));
	}

	/**
	 * A timeout while connecting has to be distinguishable from one while reading the response, because
	 * only the former guarantees that nothing was sent. The JDK reports both as SocketTimeoutException.
	 * A listening socket whose accept queue is full drops further SYNs, which is what makes the connect
	 * time out here.
	 */
	@Test
	void connectTimeoutSurfacesAsConnectTimeoutException() throws Exception {
		List<Socket> queued = new ArrayList<>();
		try (ServerSocket neverAccepting = new ServerSocket(0, 1)) {
			int port = neverAccepting.getLocalPort();
			fillAcceptQueue(port, queued);

			assertThrows(ConnectTimeoutException.class,
					() -> Connection.open("127.0.0.1", port, null, null, 200));
		} finally {
			for (Socket s : queued)
				s.close();
		}
	}

	/**
	 * Connects until the accept queue of the never-accepting socket is full, from which point on the
	 * kernel drops further SYNs rather than queueing them.
	 */
	private static void fillAcceptQueue(int port, List<Socket> queued) throws IOException {
		for (int i = 0; i < 10; i++) {
			Socket s = new Socket();
			try {
				s.connect(new InetSocketAddress("127.0.0.1", port), 200);
				queued.add(s);
			} catch (SocketTimeoutException e) {
				s.close();
				return;
			}
		}
	}

	/**
	 * A failed body read leaves the connection desynchronized: the rest of the body is still in the
	 * stream. Such a connection must be closed and detached rather than go back into the pool.
	 */
	@Test
	void bodyFailedClosesAndDetachesTheConnection() {
		Exchange exc = new Exchange(null);
		exc.setTargetConnection(conLocalhost);
		conLocalhost.setExchange(exc);

		conLocalhost.bodyFailed(new ReadingBodyException(new ClosedChannelException()));

		assertTrue(conLocalhost.isClosed());
		assertNull(exc.getTargetConnection(), "must be detached, so nothing hands it back to the pool");
	}

	@Test
	void bodyFailedIsANoOpWithoutAnExchange() {
		assertDoesNotThrow(() -> conLocalhost.bodyFailed(new ReadingBodyException(new ClosedChannelException())));

		assertFalse(conLocalhost.isClosed(), "no exchange owns this connection, so it stays usable");
	}

	/**
	 * The failure notification arrives through the observer chain: registering the connection on a body
	 * that then fails must close it, which is how the real response path reaches bodyFailed().
	 */
	@Test
	void failingBodyNotifiesTheConnectionAsObserver() {
		Exchange exc = new Exchange(null);
		exc.setTargetConnection(conLocalhost);
		conLocalhost.setExchange(exc);

		Body body = new Body(ThrowingInputStream.closedChannel("partial"), 1000);
		body.addObserver(conLocalhost);

		assertThrows(ReadingBodyException.class, body::read);

		assertTrue(conLocalhost.isClosed());
		assertNull(exc.getTargetConnection());
	}
}
