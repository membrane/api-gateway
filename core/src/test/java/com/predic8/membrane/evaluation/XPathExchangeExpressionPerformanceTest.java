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
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.lang.ExchangeExpression;
import com.predic8.membrane.core.lang.ExchangeExpression.InterceptorAdapter;
import com.predic8.membrane.core.router.DefaultRouter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.util.List;
import java.util.logging.Logger;

import static com.predic8.membrane.core.interceptor.Interceptor.Flow.REQUEST;
import static com.predic8.membrane.core.lang.ExchangeExpression.Language.XPATH;
import static com.predic8.membrane.core.lang.ExchangeExpression.expression;

/**
 * Performance regression guard for <a href="https://github.com/membrane/api-gateway/issues/3130">#3130</a>.
 * This test evaluates a handful of XPath expressions - the way several independent
 * &lt;if&gt;/&lt;setProperty&gt; plugins on the same route would - against the same request over
 * many exchanges and logs the time per exchange.
 * <p>
 * Measured results, each with {@code WARMUP_EXCHANGES = 200} and {@code MEASURED_EXCHANGES = 2_000},
 * 10 expressions against a 40-item body:
 * <ul>
 *   <li><b>Before</b> #3130 (each {@code evaluate()} re-parsed the body via
 *       {@code parser.parse(XMLUtil.getInputSource(msg))}): 0.8314 ms/exchange,
 *       83.14 &micro;s/expression, 1662.7 ms total.</li>
 *   <li><b>After</b> #3130 (evaluation routed through the {@code XmlDomBody}-cached document, so
 *       the body is parsed once per exchange rather than once per expression): 0.4726 ms/exchange,
 *       47.26 &micro;s/expression, 945.2 ms total - roughly 1.8x faster.</li>
 * </ul>
 * (For reference, the same "before" code measured 0.3072 ms/exchange, 61.45 &micro;s/expression at
 * half the scale: 5 expressions against a 20-item body.)
 */
class XPathExchangeExpressionPerformanceTest {

    private static final Logger LOGGER = Logger.getLogger(XPathExchangeExpressionPerformanceTest.class.getName());

    private static final int WARMUP_EXCHANGES = 100;
    private static final int MEASURED_EXCHANGES = 500;

    // Value extractions, as e.g. setProperty would run.
    private static final List<String> STRING_XPATHS = List.of(
            "//order/@id",
            "//order/customer/name",
            "//order/customer/email",
            "//order/items/item[1]/price",
            "//order/items/item[last()]/sku"
    );

    // Conditions, as e.g. <if test="..."> would run.
    private static final List<String> BOOLEAN_XPATHS = List.of(
            "//order/shipping/country = 'DE'",
            "count(//order/items/item) > 10",
            "//order/@id = '4711'",
            "//order/customer/email = 'jane.doe@example.com'",
            "//order/items/item[10]/quantity > 2"
    );

    private DefaultRouter router;
    private List<ExchangeExpression> stringExpressions;
    private List<ExchangeExpression> booleanExpressions;

    @BeforeEach
    void setUp() {
        router = new DefaultRouter();
        var interceptor = new InterceptorAdapter(router);
        stringExpressions = STRING_XPATHS.stream().map(xpath -> expression(interceptor, XPATH, xpath)).toList();
        booleanExpressions = BOOLEAN_XPATHS.stream().map(xpath -> expression(interceptor, XPATH, xpath)).toList();
    }

    @AfterEach
    void tearDown() {
        router.stop();
    }

    @Test
    void baseline() throws URISyntaxException {
        for (int i = 0; i < WARMUP_EXCHANGES; i++) {
            evaluateAllExpressions(buildExchange());
        }

        long start = System.nanoTime();
        for (int i = 0; i < MEASURED_EXCHANGES; i++) {
            evaluateAllExpressions(buildExchange());
        }
        long durationNanos = System.nanoTime() - start;

        int expressionsPerExchange = stringExpressions.size() + booleanExpressions.size();
        double msPerExchange = durationNanos / 1_000_000.0 / MEASURED_EXCHANGES;
        double usPerExpression = durationNanos / 1_000.0 / (MEASURED_EXCHANGES * (long) expressionsPerExchange);

        LOGGER.info(() -> "XPathExchangeExpression performance (#3130): %d exchanges x %d XPath expressions = %.4f ms/exchange, %.2f µs/expression, %.1f ms total"
                .formatted(MEASURED_EXCHANGES, expressionsPerExchange, msPerExchange, usPerExpression, durationNanos / 1_000_000.0));
    }

    private void evaluateAllExpressions(Exchange exchange) {
        for (ExchangeExpression e : stringExpressions) {
            e.evaluate(exchange, REQUEST, String.class);
        }
        for (ExchangeExpression e : booleanExpressions) {
            e.evaluate(exchange, REQUEST, Boolean.class);
        }
    }

    private Exchange buildExchange() throws URISyntaxException {
        return Request.post("/orders").xml(ORDER_XML).buildExchange();
    }

    private static final String ORDER_XML = buildOrderXml();

    private static String buildOrderXml() {
        StringBuilder items = new StringBuilder();
        for (int i = 1; i <= 40; i++) {
            items.append("""
                        <item>
                            <sku>SKU-%d</sku>
                            <name>Widget %d</name>
                            <price>%d.99</price>
                            <quantity>%d</quantity>
                        </item>
                    """.formatted(i, i, i, i % 5 + 1));
        }
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <order id="4711">
                    <customer>
                        <name>Jane Doe</name>
                        <email>jane.doe@example.com</email>
                    </customer>
                    <items>
                %s\
                    </items>
                    <shipping>
                        <country>DE</country>
                        <city>Berlin</city>
                    </shipping>
                </order>
                """.formatted(items);
    }
}
