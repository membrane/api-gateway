package com.predic8.membrane.core.lang;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.Interceptor;

/**
 * @description Returns input as output without changes (but casts to expected type).
 * Used in cases where expressions are enforced, but no calculations are desired.
 */
public class NoopExchangeExpression extends AbstractExchangeExpression{

    public NoopExchangeExpression(String source) {
        super(source);
    }

    @Override
    public <T> T evaluate(Exchange exchange, Interceptor.Flow flow, Class<T> type) {
        return type.cast(expression);
    }
}
