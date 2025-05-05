package com.predic8.membrane.core.interceptor.idempotency;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exceptions.ProblemDetails;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.lang.ExchangeExpression;
import com.predic8.membrane.core.lang.ExchangeExpression.Language;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.predic8.membrane.core.exceptions.ProblemDetails.user;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.REQUEST;
import static com.predic8.membrane.core.interceptor.Outcome.*;

@MCElement(name = "idempotency")
public class IdempotencyInterceptor extends AbstractInterceptor {

    private String key;
    private ExchangeExpression exchangeExpression;
    private Language language;
    private final Map<String, Boolean> processedKeys = new ConcurrentHashMap<>();

    @Override
    public void init() {
        super.init();
        exchangeExpression = ExchangeExpression.newInstance(router, language, key);
    }

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
        return key == null ? "" : key;
    }

    private Outcome handleDuplicateKey(Exchange exc) {
        user(false, "idempotency")
                .statusCode(400)
                .detail("Idempotency key already processed")
                .buildAndSetResponse(exc);
        return ABORT;
    }

    @Override
    public String getDisplayName() {
        return "Idempotency";
    }

    @MCAttribute
    public void setKey(String key) {
        this.key = key;
    }

    @MCAttribute
    public void setLanguage(Language language) {
        this.language = language;
    }

    public String getKey() {
        return key;
    }

    public Language getLanguage() {
        return language;
    }
}
