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

package com.predic8.membrane.core.interceptor.log;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.router.DefaultRouter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.net.URISyntaxException;
import java.util.UUID;

import static com.predic8.membrane.core.http.Request.get;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.lang.ExchangeExpression.Language.SPEL;
import static org.junit.jupiter.api.Assertions.*;

class CorrelationIdInterceptorTest {

    private CorrelationIdInterceptor interceptor;

    @BeforeEach
    void setUp() {
        MDC.clear();
        interceptor = new CorrelationIdInterceptor();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void generatesUuidWhenHeaderIsAbsent() throws URISyntaxException {
        interceptor.init(new DefaultRouter());
        Exchange exc = get("/foo").buildExchange();

        assertEquals(CONTINUE, interceptor.handleRequest(exc));

        String value = exc.getRequest().getHeader().getFirstValue("X-Correlation-Id");
        assertNotNull(value);
        assertDoesNotThrow(() -> UUID.fromString(value), "Expected a UUID but got: " + value);
        assertEquals(value, MDC.get("correlationId"));
    }

    @Test
    void generatesDistinctUuidsByDefault() throws URISyntaxException {
        interceptor.init(new DefaultRouter());

        interceptor.handleRequest(get("/a").buildExchange());
        String first = MDC.get("correlationId");
        interceptor.handleRequest(get("/b").buildExchange());
        String second = MDC.get("correlationId");

        assertNotEquals(first, second);
    }

    @Test
    void usesExistingHeaderValue() throws URISyntaxException {
        interceptor.init(new DefaultRouter());
        Exchange exc = get("/foo").header("X-Correlation-Id", "abc-123").buildExchange();

        interceptor.handleRequest(exc);

        assertEquals("abc-123", exc.getRequest().getHeader().getFirstValue("X-Correlation-Id"));
        assertEquals("abc-123", MDC.get("correlationId"));
    }

    @Test
    void blankHeaderIsReplacedWithGeneratedValue() throws URISyntaxException {
        interceptor.init(new DefaultRouter());
        Exchange exc = get("/foo").header("X-Correlation-Id", "   ").buildExchange();

        interceptor.handleRequest(exc);

        String value = exc.getRequest().getHeader().getFirstValue("X-Correlation-Id");
        assertDoesNotThrow(() -> UUID.fromString(value.trim()));
        assertEquals(value, MDC.get("correlationId"));
    }

    @Test
    void controlCharactersInHeaderAreMaskedInMdc() throws URISyntaxException {
        interceptor.init(new DefaultRouter());
        // ESC and TAB are not escaped by the header layer, so they would otherwise reach the MDC and
        // could be used for terminal-escape / log injection.
        Exchange exc = get("/foo").header("X-Correlation-Id", "abc[31m\tINJECTED").buildExchange();

        interceptor.handleRequest(exc);

        String mdc = MDC.get("correlationId");
        assertFalse(mdc.contains(""), "ESC must be masked");
        assertFalse(mdc.contains("\t"), "TAB must be masked");
        assertEquals("abc_[31m_INJECTED", mdc);
    }

    @Test
    void overlongHeaderValueIsTruncatedInMdc() throws URISyntaxException {
        interceptor.init(new DefaultRouter());
        Exchange exc = get("/foo").header("X-Correlation-Id", "x".repeat(500)).buildExchange();

        interceptor.handleRequest(exc);

        assertEquals(200, MDC.get("correlationId").length());
    }

    @Test
    void responseRemovesMdcEntry() throws URISyntaxException {
        interceptor.init(new DefaultRouter());
        Exchange exc = get("/foo").buildExchange();

        interceptor.handleRequest(exc);
        assertNotNull(MDC.get("correlationId"));

        assertEquals(CONTINUE, interceptor.handleResponse(exc));
        assertNull(MDC.get("correlationId"));
    }

    @Test
    void abortRemovesMdcEntry() throws URISyntaxException {
        interceptor.init(new DefaultRouter());
        Exchange exc = get("/foo").buildExchange();

        interceptor.handleRequest(exc);
        assertNotNull(MDC.get("correlationId"));

        interceptor.handleAbort(exc);
        assertNull(MDC.get("correlationId"));
    }

    @Test
    void honoursCustomHeaderAndLogField() throws URISyntaxException {
        interceptor.setHeader("X-Request-Id");
        interceptor.setLogField("requestId");
        interceptor.init(new DefaultRouter());
        Exchange exc = get("/foo").buildExchange();

        interceptor.handleRequest(exc);

        String value = exc.getRequest().getHeader().getFirstValue("X-Request-Id");
        assertNotNull(value);
        assertEquals(value, MDC.get("requestId"));
    }

    @Test
    void honoursCustomDefaultSpELExpression() throws URISyntaxException {
        interceptor.setLanguage(SPEL);
        interceptor.setDefaultValue("${'fixed-' + 'id'}");
        interceptor.init(new DefaultRouter());
        Exchange exc = get("/foo").buildExchange();

        interceptor.handleRequest(exc);

        assertEquals("fixed-id", exc.getRequest().getHeader().getFirstValue("X-Correlation-Id"));
        assertEquals("fixed-id", MDC.get("correlationId"));
    }
}
