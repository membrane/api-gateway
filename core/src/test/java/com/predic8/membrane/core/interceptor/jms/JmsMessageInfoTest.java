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

package com.predic8.membrane.core.interceptor.jms;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.lang.ExchangeExpression.InterceptorAdapter;
import com.predic8.membrane.core.lang.ExchangeExpression.Language;
import com.predic8.membrane.core.router.DefaultRouter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.predic8.membrane.core.http.Request.post;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.REQUEST;
import static com.predic8.membrane.core.interceptor.jms.JmsConsumerExtension.JMS_PROPERTY;
import static com.predic8.membrane.core.lang.ExchangeExpression.Language.GROOVY;
import static com.predic8.membrane.core.lang.ExchangeExpression.Language.SPEL;
import static com.predic8.membrane.core.lang.ExchangeExpression.expression;
import static org.junit.jupiter.api.Assertions.*;

/**
 * A flow reads the received message with <code>property.jms</code>.
 */
class JmsMessageInfoTest {

    private final DefaultRouter router = new DefaultRouter();
    private Exchange exchange;

    @BeforeEach
    void setUp() throws Exception {
        exchange = post("/").buildExchange();
        exchange.setProperty(JMS_PROPERTY, JmsMessageInfo.builder()
                .messageId("ID:1")
                .destination("orders")
                .redelivered(true)
                .deliveryCount(2)
                .timestamp(1_700_000_000_000L)
                .property("orderId", 7)
                .property("tenant", "acme")
                .build());
    }

    @AfterEach
    void tearDown() {
        router.stop();
    }

    private Object eval(final Language language, final String source) {
        return expression(new InterceptorAdapter(router), language, source).evaluate(exchange, REQUEST, Object.class);
    }

    @Test
    void spelReadsHeaderFields() {
        assertEquals("ID:1", eval(SPEL, "property.jms.messageId"));
        assertEquals("orders", eval(SPEL, "property.jms.destination"));
        assertEquals(true, eval(SPEL, "property.jms.redelivered"));
        assertEquals(2, eval(SPEL, "property.jms.deliveryCount"));
        assertEquals(1_700_000_000_000L, eval(SPEL, "property.jms.timestamp"));
        assertEquals(4, eval(SPEL, "property.jms.priority"));
        assertNull(eval(SPEL, "property.jms.correlationId"));
    }

    @Test
    void spelReadsPropertiesWithTheirType() {
        assertEquals(7, eval(SPEL, "property.jms.properties.orderId"));
        assertEquals("acme", eval(SPEL, "property.jms.properties['tenant']"));
        assertNull(eval(SPEL, "property.jms.properties.missing"));
        assertEquals(true, eval(SPEL, "property.jms.deliveryCount > 1 and property.jms.properties.orderId == 7"));
    }

    @Test
    void groovyReadsHeaderFieldsAndProperties() {
        assertEquals("ID:1", eval(GROOVY, "property.jms.messageId"));
        assertEquals(7, eval(GROOVY, "property.jms.properties.orderId"));
    }

    @Test
    void propertiesAreReadOnly() {
        final var info = JmsMessageInfo.builder().property("orderId", 7).build();

        assertThrows(UnsupportedOperationException.class, () -> info.getProperties().put("orderId", 8));
    }
}
