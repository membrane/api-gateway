package com.predic8.membrane.core.interceptor.idempotency;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.lang.AbstractExchangeExpressionInterceptor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.predic8.membrane.core.interceptor.Interceptor.Flow.REQUEST;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;

@MCElement(name = "idempotency")
public class IdempotencyInterceptor extends AbstractExchangeExpressionInterceptor {

    private static final Map<String, Boolean> processedKeys = new ConcurrentHashMap<>();

    @Override
    public Outcome handleRequest(Exchange exc) {
        String key = normalizeKey(exchangeExpression.evaluate(exc, REQUEST, String.class));

        if (key.isEmpty()) {
            return CONTINUE;
        }

        if (processedKeys.containsKey(key)) {
            return handleDuplicateKey(exc);
        }

        processedKeys.put(key, true);
        return CONTINUE;
    }

    private String normalizeKey(String key) {
        return "null".equals(key) ? "" : key;
    }

    private Outcome handleDuplicateKey(Exchange exc) {
        exc.setResponse(Response.statusCode(400).body("Idempotency key already processed").build());
        return ABORT;
    }

    @MCAttribute
    public void setKey(String keyExpression) {
        this.expression = keyExpression;
    }

    public String getKey() {
        return this.expression;
    }
}
