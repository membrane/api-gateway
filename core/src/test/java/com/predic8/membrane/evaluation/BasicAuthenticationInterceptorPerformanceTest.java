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

package com.predic8.membrane.evaluation;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.authentication.BasicAuthenticationInterceptor;
import com.predic8.membrane.core.interceptor.authentication.session.StaticUserDataProvider.UserConfig;
import com.predic8.membrane.core.router.DefaultRouter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.util.List;
import java.util.logging.Logger;

import static com.predic8.membrane.core.http.Header.AUTHORIZATION;
import static com.predic8.membrane.core.http.Request.get;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Base64.getEncoder;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Measures the time {@link BasicAuthenticationInterceptor#handleRequest(Exchange)} takes per request
 * for the typical outcomes: a plaintext password, a SHA-512 crypt(3) hashed password, a wrong password
 * and a missing <code>Authorization</code> header. Exchanges are built before the timed section, so only
 * the interceptor itself is measured. Results are logged, nothing is asserted about the timing.
 */
class BasicAuthenticationInterceptorPerformanceTest {

    private static final Logger LOGGER = Logger.getLogger(BasicAuthenticationInterceptorPerformanceTest.class.getName());

    // Enough iterations for the JIT to reach steady state; shorter runs overstate the time per request.
    private static final int WARMUP_REQUESTS = 100_000;
    private static final int MEASURED_REQUESTS = 200_000;

    // crypt(3) SHA-512 with the default 5000 rounds is deliberately slow, so fewer iterations are enough.
    private static final int HASHED_WARMUP_REQUESTS = 500;
    private static final int HASHED_MEASURED_REQUESTS = 2_000;

    // Exchanges are built in batches of this size to keep memory bounded.
    private static final int BATCH_SIZE = 10_000;

    // SHA-512 crypt hash of "admin"
    private static final String SHA512_HASH = "$6$jd3$AHVA4BVU0wTtVmF6ocQLSvxds455z0RKeNG/3Y0kF8C9AmAqyo8WBhEDpZ3JjO3k3lX/t5MB0NQrqGDpQyQf.1";

    private DefaultRouter router;
    private BasicAuthenticationInterceptor interceptor;

    @BeforeEach
    void setUp() {
        router = new DefaultRouter();
        interceptor = new BasicAuthenticationInterceptor();
        interceptor.setUsers(List.of(
                new UserConfig("alice", "secret"),
                new UserConfig("klara", SHA512_HASH)
        ));
        interceptor.init(router);
    }

    @AfterEach
    void tearDown() {
        router.stop();
    }

    @Test
    void plaintextPassword() throws URISyntaxException {
        measure("plaintext password", "alice", "secret", CONTINUE, WARMUP_REQUESTS, MEASURED_REQUESTS);
    }

    @Test
    void hashedPassword() throws URISyntaxException {
        measure("SHA-512 crypt password", "klara", "admin", CONTINUE, HASHED_WARMUP_REQUESTS, HASHED_MEASURED_REQUESTS);
    }

    @Test
    void wrongPassword() throws URISyntaxException {
        measure("wrong password", "alice", "wrong", ABORT, WARMUP_REQUESTS, MEASURED_REQUESTS);
    }

    @Test
    void missingAuthorizationHeader() throws URISyntaxException {
        measure("missing Authorization header", null, null, ABORT, WARMUP_REQUESTS, MEASURED_REQUESTS);
    }

    private void measure(String scenario, String username, String password, Outcome expected,
                         int warmupRequests, int measuredRequests) throws URISyntaxException {
        run(username, password, expected, warmupRequests);
        long durationNanos = run(username, password, expected, measuredRequests);

        double usPerRequest = durationNanos / 1_000.0 / measuredRequests;
        double requestsPerSecond = measuredRequests / (durationNanos / 1_000_000_000.0);

        LOGGER.info(() -> "BasicAuthenticationInterceptor performance (%s): %d requests = %.2f µs/request, %.0f requests/s, %.1f ms total"
                .formatted(scenario, measuredRequests, usPerRequest, requestsPerSecond, durationNanos / 1_000_000.0));
    }

    /**
     * @return nanoseconds spent in the interceptor, excluding building the exchanges
     */
    private long run(String username, String password, Outcome expected, int requests) throws URISyntaxException {
        long durationNanos = 0;
        for (int done = 0; done < requests; done += BATCH_SIZE) {
            Exchange[] exchanges = buildExchanges(username, password, Math.min(BATCH_SIZE, requests - done));
            long start = System.nanoTime();
            for (Exchange exchange : exchanges) {
                assertEquals(expected, interceptor.handleRequest(exchange));
            }
            durationNanos += System.nanoTime() - start;
        }
        return durationNanos;
    }

    private static Exchange[] buildExchanges(String username, String password, int count) throws URISyntaxException {
        Exchange[] exchanges = new Exchange[count];
        for (int i = 0; i < count; i++) {
            var builder = get("/foo");
            if (username != null)
                builder.header(AUTHORIZATION, "Basic " + getEncoder().encodeToString((username + ":" + password).getBytes(UTF_8)));
            exchanges[i] = builder.buildExchange();
        }
        return exchanges;
    }
}
