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

package com.predic8.membrane.core.interceptor.flow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.ReadingBodyException;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.log.LogInterceptor;
import com.predic8.membrane.core.proxies.ServiceProxy;
import com.predic8.membrane.core.router.DummyTestRouter;
import com.predic8.membrane.core.router.Router;
import com.predic8.membrane.core.router.TestRouter;
import com.predic8.membrane.test.TestAppender;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Logger;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Callable;

import static com.predic8.membrane.core.exceptions.ProblemDetails.*;
import static com.predic8.membrane.core.http.Request.get;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.router.DummyTestRouter.productionRouter;
import static org.junit.jupiter.api.Assertions.*;

class AbstractFlowInterceptorTest {

    @Test
    void propagationOfProxyAndRouterToNestedInterceptors() {
        var r = new TestRouter();
        var p = new ServiceProxy();
        var ri = new RequestInterceptor();
        var ti = new LogInterceptor();
        ri.setFlow(List.of(ti));
        ri.init(r,p);
        assertSame(r,ti.getRouter());
        assertSame(p,ti.getProxy());
    }

    @Test
    void nestedExceptionHidesDetailsInProduction() throws Exception {
        var exc = get("/foo").buildExchange();

        assertEquals(ABORT, requestFlowThrowing(productionRouter(), new RuntimeException("secret")).handleRequest(exc));

        var json = parse(exc);
        assertEquals(500, exc.getResponse().getStatusCode());
        assertFalse(json.has(STACK_TRACE));
        assertFalse(json.has(MESSAGE));
        assertFalse(json.has(ATTENTION));
        assertTrue(json.get(DETAIL).asText().contains("key"));
    }

    @Test
    void nestedExceptionNamesPluginAndFlow() throws Exception {
        var exc = get("/foo?api_key=secret").buildExchange();

        requestFlowThrowing(new DummyTestRouter(), new RuntimeException("boom")).handleRequest(exc);

        assertEquals("Aborting! Exception caused in thrower during REQUEST flow.", parse(exc).get(DETAIL).asText());
    }

    @Test
    void nestedExceptionDoesNotLogRequestUriInProduction() throws Exception {
        final var exc = get("/foo?api_key=secret").buildExchange();

        final var log = captureLog(() -> requestFlowThrowing(productionRouter(), new RuntimeException("boom")).handleRequest(exc));

        assertTrue(log.contains("thrower"));
        assertFalse(log.contains("api_key=secret"), log.getMessages().toString());
    }

    @Test
    void nestedReadingBodyExceptionDoesNotLogRequestUri() throws Exception {
        final var exc = get("/foo?api_key=secret").buildExchange();

        final var log = captureLog(() -> requestFlowThrowing(new DummyTestRouter(),
                new ReadingBodyException(new IOException("broken"), exc.getRequest())).handleRequest(exc));

        assertTrue(log.contains("thrower"));
        assertFalse(log.contains("api_key=secret"), log.getMessages().toString());
    }

    @Test
    void nestedReadingBodyExceptionIsBodyFailure() throws Exception {
        var exc = get("/foo").buildExchange();

        requestFlowThrowing(new DummyTestRouter(), new ReadingBodyException(new IOException("broken"), exc.getRequest())).handleRequest(exc);

        assertEquals(400, exc.getResponse().getStatusCode());
        assertTrue(parse(exc).get(SEE).asText().endsWith("reading-body"));
    }

    private static RequestInterceptor requestFlowThrowing(Router router, RuntimeException e) {
        final var thrower = new AbstractInterceptor() {
            @Override
            public Outcome handleRequest(Exchange exc) {
                throw e;
            }
        };
        thrower.setDisplayName("thrower");
        final var ri = new RequestInterceptor();
        ri.setFlow(List.of(thrower));
        ri.init(router, new ServiceProxy());
        return ri;
    }

    private static TestAppender captureLog(Callable<?> action) throws Exception {
        final var root = (Logger) LogManager.getRootLogger();
        final var appender = new TestAppender("AbstractFlowInterceptorTest");
        appender.start();
        root.addAppender(appender);
        try {
            action.call();
        } finally {
            root.removeAppender(appender);
            appender.stop();
        }
        return appender;
    }

    private static JsonNode parse(Exchange exc) throws IOException {
        return new ObjectMapper().readTree(exc.getResponse().getBodyAsStringDecoded());
    }
}
