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

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.lang.AbstractExchangeExpressionInterceptor;
import org.slf4j.MDC;

import java.util.UUID;

import static com.predic8.membrane.core.interceptor.Interceptor.Flow.REQUEST;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.util.text.StringUtil.maskNonPrintableCharacters;
import static com.predic8.membrane.core.util.text.StringUtil.truncateAfter;

/**
 * @description Reads a correlation id from a request header (or generates one if absent), writes it
 * back into that header and adds it to the SLF4J MDC logging context so it appears in every log line
 * of the request. The MDC entry is removed again on the response (and on abort).
 * @topic 4. Logging
 */
@MCElement(name = "correlationId")
public class CorrelationIdInterceptor extends AbstractExchangeExpressionInterceptor {

    /**
     * Upper bound for a correlation id taken from the request header. Long enough for a UUID and
     * common id formats, while capping attacker-controlled input that ends up in the logs.
     */
    private static final int MAX_HEADER_VALUE_LENGTH = 200;

    private String header = "X-Correlation-Id";
    private String logField = "correlationId";

    public CorrelationIdInterceptor() {
        name = "correlationId";
    }

    @Override
    public Outcome handleRequest(Exchange exc) {
        String value = exc.getRequest().getHeader().getFirstValue(header);
        if (value == null || value.isBlank()) {
            value = generateId(exc);
            if (value != null)
                exc.getRequest().getHeader().setValue(header, value);
        } else {
            // Sanitize attacker-controlled header input before it reaches the MDC to prevent
            // CR/LF log forging and to cap excessive length.
            value = maskNonPrintableCharacters(truncateAfter(value, MAX_HEADER_VALUE_LENGTH));
        }
        if (value != null)
            MDC.put(logField, value);
        return CONTINUE;
    }

    /**
     * Generates a new correlation id: a random UUID by default, or the result of the configured
     * 'default' expression if one was set.
     */
    private String generateId(Exchange exc) {
        if (expression == null || expression.isBlank())
            return UUID.randomUUID().toString();
        return exchangeExpression.evaluate(exc, REQUEST, String.class);
    }

    @Override
    public Outcome handleResponse(Exchange exc) {
        MDC.remove(logField);
        return CONTINUE;
    }

    @Override
    public void handleAbort(Exchange exc) {
        // Avoid leaking the MDC entry into a pooled thread when the exchange is aborted.
        MDC.remove(logField);
    }

    public String getHeader() {
        return header;
    }

    /**
     * @description Name of the HTTP request header that carries the correlation id.
     * @default X-Correlation-Id
     */
    @MCAttribute
    public void setHeader(String header) {
        this.header = header;
    }

    public String getLogField() {
        return logField;
    }

    /**
     * @description Name of the MDC variable the correlation id is stored under for logging.
     * @default correlationId
     */
    @MCAttribute
    public void setLogField(String logField) {
        this.logField = logField;
    }

    public String getDefaultValue() {
        return expression;
    }

    /**
     * @description Expression used to generate a new correlation id when the header is absent.
     * If omitted, a random UUID is generated.
     */
    @MCAttribute(attributeName = "default")
    public void setDefaultValue(String value) {
        this.expression = value;
    }

    @Override
    public String getShortDescription() {
        return "Sets the '%s' header as correlation id and exposes it in the MDC field '%s'.".formatted(header, logField);
    }
}
