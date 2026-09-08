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
package com.predic8.membrane.core.interceptor;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.interceptor.Interceptor.Flow;
import com.predic8.membrane.core.router.DummyTestRouter;
import com.predic8.membrane.core.router.Router;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static com.predic8.membrane.core.interceptor.FlowController.ABORTION_REASON;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The reverse walk of a chain: which handler each interceptor gets and what the chain reports
 * back to its caller when the response flow aborts.
 */
class FlowControllerTest {

    private final List<String> calls = new ArrayList<>();

    private Exchange exchange;

    @BeforeEach
    void setUp() throws URISyntaxException {
        exchange = new Request.Builder().get("/foo").buildExchange();
    }

    private FlowController controller() {
        return new FlowController(new DummyTestRouter());
    }

    @Test
    void responseAbortUnwindsRemainingWithAbortHandlers() {
        List<Interceptor> chain = List.of(probe("a"), probe("b"), abortingInResponse("c"));

        assertEquals(ABORT, controller().invokeResponseHandlers(exchange, chain));

        // "c" aborted itself, so it does not get its own abort handler called
        assertEquals(List.of("c:response", "b:abort", "a:abort"), calls);
    }

    @Test
    void responseExceptionAbortsAndBuildsErrorResponse() {
        RuntimeException failure = new RuntimeException("boom");
        List<Interceptor> chain = List.of(probe("a"), probe("b"), throwingInResponse("c", failure));

        assertEquals(ABORT, controller().invokeResponseHandlers(exchange, chain));

        assertEquals(List.of("c:response", "b:abort", "a:abort"), calls);
        assertNotNull(exchange.getResponse());
        assertEquals(500, exchange.getResponse().getStatusCode());
        assertSame(failure, exchange.getProperty(ABORTION_REASON));
    }

    @Test
    void abortHandlerFailureDoesNotStopUnwinding() {
        List<Interceptor> chain = List.of(probe("a"), failingInAbort("b"), abortingInResponse("c"));

        assertEquals(ABORT, controller().invokeResponseHandlers(exchange, chain));

        // "a" is still unwound although "b" threw in its abort handler
        assertEquals(List.of("c:response", "b:abort", "a:abort"), calls);
    }

    @Test
    void responseAbortAfterReturnIsReportedAsAbort() {
        List<Interceptor> chain = List.of(probe("a"), abortingInResponse("b"), returningInRequest("c"));

        assertEquals(ABORT, controller().invokeRequestHandlers(exchange, chain));

        assertEquals(List.of("a:request", "b:request", "c:request", "b:response", "a:abort"), calls);
    }

    @Test
    void abortUnwindingIgnoresTheAppliedFlow() {
        Probe requestOnly = probe("a");
        requestOnly.setAppliedFlow(EnumSet.of(Flow.REQUEST));
        List<Interceptor> chain = List.of(requestOnly, probe("b"), abortingInResponse("c"));

        assertEquals(ABORT, controller().invokeResponseHandlers(exchange, chain));

        // "a" handles no responses, but an aborting chain unwinds it all the same
        assertEquals(List.of("c:response", "b:abort", "a:abort"), calls);
    }

    @Test
    void responseFlowWithoutAbortContinues() {
        List<Interceptor> chain = List.of(probe("a"), probe("b"));

        assertEquals(CONTINUE, controller().invokeResponseHandlers(exchange, chain));

        assertEquals(List.of("b:response", "a:response"), calls);
    }

    @Test
    void productionModeKeepsExceptionDetailOutOfTheResponse() {
        Router production = DummyTestRouter.productionRouter();
        List<Interceptor> chain = List.of(probe("a"), throwingInResponse("c", new RuntimeException("boom")));

        assertEquals(ABORT, new FlowController(production).invokeResponseHandlers(exchange, chain));

        String body = exchange.getResponse().getBodyAsStringDecoded();
        assertFalse(body.contains("boom"), body);
    }

    private Probe probe(String name) {
        return new Probe(name);
    }

    private Probe returningInRequest(String name) {
        return new Probe(name).requestOutcome(RETURN);
    }

    private Probe abortingInResponse(String name) {
        return new Probe(name).responseOutcome(ABORT);
    }

    private Probe throwingInResponse(String name, RuntimeException failure) {
        return new Probe(name).responseFailure(failure);
    }

    private Probe failingInAbort(String name) {
        return new Probe(name).failInAbort();
    }

    /**
     * Records every handler it receives into the enclosing test's call log.
     */
    private class Probe extends AbstractInterceptor {

        private Outcome requestOutcome = CONTINUE;
        private Outcome responseOutcome = CONTINUE;
        private RuntimeException responseFailure;
        private boolean failInAbort;

        private Probe(String name) {
            setDisplayName(name);
        }

        private Probe requestOutcome(Outcome outcome) {
            requestOutcome = outcome;
            return this;
        }

        private Probe responseOutcome(Outcome outcome) {
            responseOutcome = outcome;
            return this;
        }

        private Probe responseFailure(RuntimeException failure) {
            responseFailure = failure;
            return this;
        }

        private Probe failInAbort() {
            failInAbort = true;
            return this;
        }

        @Override
        public Outcome handleRequest(Exchange exc) {
            calls.add(getDisplayName() + ":request");
            return requestOutcome;
        }

        @Override
        public Outcome handleResponse(Exchange exc) {
            calls.add(getDisplayName() + ":response");
            if (responseFailure != null)
                throw responseFailure;
            return responseOutcome;
        }

        @Override
        public void handleAbort(Exchange exc) {
            calls.add(getDisplayName() + ":abort");
            if (failInAbort)
                throw new RuntimeException("fail in abort");
        }
    }
}
