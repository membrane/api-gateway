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
        String key;
        key = getKeyValue(exchangeExpression.evaluate(exc, REQUEST, String.class));
        if (key.isEmpty()) {
            exc.setResponse(Response.statusCode(400).body("Idempotency key is missing or empty").build());
            return ABORT;
        }

        if (processedKeys.containsKey(key)) {
            exc.setResponse(Response.statusCode(400).body("Idempotency key already processed").build());
            return ABORT;
        }
        processedKeys.put(key, true);
        return CONTINUE;
    }

    private String getKeyValue(String key) {
        return key.equals("null") ? "" : key;
    }

    @MCAttribute
    public void setKey(String key) {
        this.expression = key;
    }

    public String getKey() {
        return this.expression;
    }
}
