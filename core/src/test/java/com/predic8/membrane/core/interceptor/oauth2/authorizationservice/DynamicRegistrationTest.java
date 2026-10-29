/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor.oauth2.authorizationservice;

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.proxies.*;
import com.predic8.membrane.core.router.*;
import com.predic8.membrane.core.transport.http.*;
import org.junit.jupiter.api.*;

import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;

import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static com.predic8.membrane.core.http.Response.internalServerError;
import static com.predic8.membrane.core.http.Response.ok;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.interceptor.Outcome.RETURN;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

class DynamicRegistrationTest {

	private static final int BACKEND_PORT = 3071;
	private static final String BACKEND_URL = "http://localhost:%d/".formatted(BACKEND_PORT);
	private static final String BACKEND_BODY = """
			{"client_id":"abc","client_secret":"secret"}""";

	private final Set<Integer> backendConnections = Collections.synchronizedSet(new HashSet<>()); // hashcodes of all connections the backend received
	private Router router;

	@BeforeEach
	void setUp() throws Exception {
		router = new TestRouter();
		final var sp = new ServiceProxy(new ServiceProxyKey(BACKEND_PORT), null, 0);
		sp.getFlow().add(new AbstractInterceptor() {
			@Override
			public Outcome handleRequest(Exchange exc) {
				backendConnections.add(((HttpServerHandler) exc.getHandler()).getSrcOut().hashCode());
				if (exc.getRequest().getUri().equals("/error"))
					exc.setResponse(internalServerError().contentType(APPLICATION_JSON).body("{}").build());
				else
					exc.setResponse(ok().contentType(APPLICATION_JSON).body(BACKEND_BODY).build());
				return RETURN;
			}
		});
		router.add(sp);
		router.start();
	}

	@AfterEach
	void tearDown() {
		router.stop();
	}

	/**
	 * @param responseHandler run as the response flow of the registration
	 */
	private DynamicRegistration createRegistration(Function<Exchange, Outcome> responseHandler) {
		final var registration = new DynamicRegistration();
		registration.setFlow(List.of(new AbstractInterceptor() {
			@Override
			public Outcome handleResponse(Exchange exc) {
				return responseHandler.apply(exc);
			}
		}));
		registration.init(router);
		return registration;
	}

	private static String retrieve(DynamicRegistration registration) throws Exception {
		return retrieve(registration, BACKEND_URL);
	}

	private static String retrieve(DynamicRegistration registration, String url) throws Exception {
		try (final var in = registration.retrieveOpenIDConfiguration(url)) {
			return new String(in.readAllBytes(), UTF_8);
		}
	}

	@Test
	void returnsBackendResponse() throws Exception {
		assertEquals(BACKEND_BODY, retrieve(createRegistration(exc -> CONTINUE)));
	}

	@Test
	void registersClient() throws Exception {
		final var client = createRegistration(exc -> CONTINUE).registerWithCallbackAt(List.of("http://localhost/callback"), BACKEND_URL);

		assertEquals("abc", client.getClientId());
		assertEquals("secret", client.getClientSecret());
	}

	@Test
	void abortInResponseFlowFails() {
		final var registration = createRegistration(exc -> ABORT);

		assertThrows(RuntimeException.class, () -> retrieve(registration));
	}

	@Test
	void connectionIsReleasedAfterAbortInResponseFlow() throws Exception {
		final var abort = new AtomicBoolean(true);
		final var registration = createRegistration(exc -> abort.get() ? ABORT : CONTINUE);
		assertThrows(RuntimeException.class, () -> retrieve(registration));

		abort.set(false);
		assertEquals(BACKEND_BODY, retrieve(registration));

		assertEquals(1, backendConnections.size());
	}

	@Test
	void unsuccessfulStatusFails() {
		final var registration = createRegistration(exc -> CONTINUE);

		assertThrows(RuntimeException.class, () -> retrieve(registration, BACKEND_URL + "error"));
	}

	@Test
	void connectionIsReleasedAfterUnsuccessfulStatus() throws Exception {
		final var registration = createRegistration(exc -> CONTINUE);
		assertThrows(RuntimeException.class, () -> retrieve(registration, BACKEND_URL + "error"));

		assertEquals(BACKEND_BODY, retrieve(registration));

		assertEquals(1, backendConnections.size());
	}

	@Test
	void exceptionInResponseFlowFails() {
		final var registration = createRegistration(exc -> {
			throw new IllegalStateException("response flow failed");
		});

		assertThrows(RuntimeException.class, () -> retrieve(registration));
	}

	@Test
	void responseReplacedInResponseFlowIsReturned() throws Exception {
		final var registration = createRegistration(exc -> {
			exc.setResponse(ok("replaced").build());
			return CONTINUE;
		});

		assertEquals("replaced", retrieve(registration));
	}

	@Test
	void errorResponseSetInResponseFlowFails() {
		final var registration = createRegistration(exc -> {
			exc.setResponse(internalServerError().build());
			return CONTINUE;
		});

		assertThrows(RuntimeException.class, () -> retrieve(registration));
	}
}
